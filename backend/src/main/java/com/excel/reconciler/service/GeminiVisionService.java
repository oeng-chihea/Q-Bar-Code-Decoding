package com.excel.reconciler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class GeminiVisionService {
    private static final Logger log = LoggerFactory.getLogger(GeminiVisionService.class);
    private static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    // Tried in this order after the configured model. Every model has its own daily quota, so listing
    // different models adds free requests; listing an alias of one already tried would only repeat it.
    private static final List<String> FALLBACK_MODELS =
            List.of("gemini-3.5-flash-lite", "gemini-3.1-flash-lite");
    // After a model reports its daily quota used up it is skipped for this long, then tried once again
    private static final Duration QUOTA_RECHECK_INTERVAL = Duration.ofMinutes(15);
    private static final Duration BUSY_COOLDOWN = Duration.ofSeconds(60);
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-3.5-flash-lite}")
    private String model = DEFAULT_MODEL;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String baseUrl = DEFAULT_BASE_URL;

    @Value("${gemini.api.timeout-seconds:40}")
    private int timeoutSeconds = 40;

    @Value("${gemini.api.media-resolution:}")
    private String mediaResolution = "";

    @Value("${gemini.api.date-read-timeout-seconds:30}")
    private int dateReadTimeoutSeconds = 30;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final Map<String, Instant> quotaExhaustedUntil = new ConcurrentHashMap<>();
    private final Map<String, Instant> busyUntil = new ConcurrentHashMap<>();

    public record BarcodeImage(byte[] imageBytes, String mimeType) {
    }

    /**
     * @param dailyQuotaExhausted every model that was tried (or skipped) is out of its free-tier requests for
     *                            the day, so repeating the call cannot help until the quota resets
     */
    public record JsonResponse(int statusCode, String content, String errorMessage, String usedModel,
                               boolean dailyQuotaExhausted) {
        public JsonResponse(int statusCode, String content, String errorMessage, String usedModel) {
            this(statusCode, content, errorMessage, usedModel, false);
        }

        public JsonResponse(int statusCode, String content, String errorMessage) {
            this(statusCode, content, errorMessage, null, false);
        }

        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300 && content != null && !content.isBlank();
        }
    }

    public GeminiVisionService(ObjectMapper objectMapper,
                               @Qualifier("imageDecoderExecutor") Executor executor) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    /**
     * Extracts barcodes from a single image using Gemini 2.5 Flash with structured output.
     */
    public List<String> extractBarcodesWithGemini(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return Collections.emptyList();
        }

        String prompt = """
                You are an expert barcode and QR code reader for inventory reconciliation.
                Inspect the image carefully, including rotated, tilted, blurry, or partially obscured labels.

                Extract ONLY barcode or QR payloads that are actually encoded in the image.
                Return each payload exactly as encoded, including letters, leading zeros, and hyphens.
                Do not return printed text, product names, SKU descriptions, Khmer text, outlet names, prices, or phone numbers.
                """;

        Map<String, Object> schema = Map.of(
                "type", "ARRAY",
                "items", Map.of("type", "STRING")
        );

        JsonResponse response = generateJson(imageBytes, mimeType, prompt, schema);
        if (!response.isSuccessful()) {
            log.warn("Gemini barcode extraction failed with status {}: {}",
                    response.statusCode(), response.errorMessage());
            return Collections.emptyList();
        }

        List<String> parsed = parseBarcodeNumbers(response.content());
        if (!parsed.isEmpty()) {
            log.info("Gemini {} successfully extracted {} codes: {}",
                    getConfiguredModel(), parsed.size(), parsed);
        }
        return parsed;
    }

    /**
     * Processes images that failed local ZXing concurrently in parallel across worker threads.
     * Preserves the exact input order so results can be aligned with uploads.
     */
    public List<List<String>> extractBarcodesParallel(List<BarcodeImage> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("Dispatching parallel Gemini {} vision requests for {} fallback images",
                getConfiguredModel(), images.size());

        List<CompletableFuture<List<String>>> futures = images.stream()
                .map(image -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return extractBarcodesWithGemini(image.imageBytes(), image.mimeType());
                    } catch (Exception e) {
                        log.warn("Gemini parallel extraction error for image: {}", e.getMessage());
                        return Collections.<String>emptyList();
                    }
                }, executor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * Outcome of reading a receipt's CREATED date. {@code failed} means the request itself did not work
     * (rate limit, busy server, timeout), so an empty {@code text} is only "no date on the receipt"
     * when {@code failed} is false. {@code quotaExhausted} marks a failure that will not clear until the
     * daily quota resets.
     */
    public record ReceiptDateRead(String text, String waybillNo, String customerName,
                                  boolean failed, boolean quotaExhausted) {
        public static ReceiptDateRead found(String text) {
            return found(text, "");
        }

        public static ReceiptDateRead found(String text, String waybillNo) {
            return found(text, waybillNo, "");
        }

        public static ReceiptDateRead found(String text, String waybillNo, String customerName) {
            return new ReceiptDateRead(text == null ? "" : text, waybillNo == null ? "" : waybillNo,
                    customerName == null ? "" : customerName, false, false);
        }

        public static ReceiptDateRead failure() {
            return new ReceiptDateRead("", "", "", true, false);
        }

        /** Failed because the free-tier daily quota is used up, which retrying cannot fix. */
        public static ReceiptDateRead quotaUsedUp() {
            return new ReceiptDateRead("", "", "", true, true);
        }
    }

    public ReceiptDateRead extractReceiptCreatedText(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return ReceiptDateRead.found("");
        }
        return extractReceiptCreatedBatch(List.of(new BarcodeImage(imageBytes, mimeType))).get(0);
    }

    public List<ReceiptDateRead> extractReceiptCreatedBatch(List<BarcodeImage> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }

        String prompt = """
                The following %d images are printed parcel receipts, photographed or scanned, each labelled
                "Receipt 1", "Receipt 2" and so on. For every receipt, return one entry with:
                - receipt: the receipt's number from its label.
                - createdText: the date and time printed next to the line labelled "CREATED", exactly as printed,
                  for example "Oct 18, 2026, 02:29 PM". Ignore any hand-drawn marks or circles around it.
                  If there is no "CREATED" label with a date, return an empty string.
                - waybillNo: the waybill (tracking) number printed on that receipt as text, for example
                  "J01419454153", exactly as printed. If it is not printed or not readable, return an empty string.
                - customerName: the customer's name on the shop receipt. Under the receiver label "អ្នកទទួល" the
                  receipt prints the customer's phone number, then the address or place, then the name on the
                  last line, for example "Nhim Sarey". Return only the name, exactly as
                  printed (Khmer or Latin): never the phone number, the address, the shop's own details or the
                  courier sticker. If no name is printed or it is not readable, return an empty string.
                Read each receipt only from its own image. Never guess, and never return any other date or number.
                """.formatted(images.size());

        Map<String, Object> entry = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "receipt", Map.of("type", "INTEGER"),
                        "createdText", Map.of("type", "STRING"),
                        "waybillNo", Map.of("type", "STRING"),
                        "customerName", Map.of("type", "STRING")),
                "required", List.of("receipt", "createdText", "waybillNo", "customerName")
        );
        Map<String, Object> schema = Map.of(
                "type", "OBJECT",
                "properties", Map.of("receipts", Map.of("type", "ARRAY", "items", entry)),
                "required", List.of("receipts")
        );

        JsonResponse response = generateJson(images, prompt, schema, null, dateReadTimeoutSeconds);
        if (response.isSuccessful()) {
            try {
                List<ReceiptDateRead> readings = new ArrayList<>(
                        Collections.nCopies(images.size(), ReceiptDateRead.failure()));
                for (JsonNode node : objectMapper.readTree(response.content()).path("receipts")) {
                    int index = images.size() == 1 ? 0 : node.path("receipt").asInt(0) - 1;
                    if (index >= 0 && index < images.size()) {
                        readings.set(index, ReceiptDateRead.found(
                                node.path("createdText").asText("").trim(), node.path("waybillNo").asText("").trim(),
                                node.path("customerName").asText("").trim()));
                    }
                }
                return readings;
            } catch (Exception e) {
                log.debug("Gemini receipt date response parse failed: {}", e.getMessage());
            }
        } else if (response.dailyQuotaExhausted()) {
            log.warn("Gemini daily quota is used up for every model; not reading the receipt dates");
            return Collections.nCopies(images.size(), ReceiptDateRead.quotaUsedUp());
        } else {
            log.warn("Gemini receipt date extraction for {} receipt(s) failed with status {}: {}",
                    images.size(), response.statusCode(), response.errorMessage());
        }
        return Collections.nCopies(images.size(), ReceiptDateRead.failure());
    }

    /**
     * Calls Gemini 2.5 Flash API with multimodal image data and structured JSON schema.
     */
    private static final String KHMER_LOGISTICS_SYSTEM_INSTRUCTION = """
            You are an expert OCR document digitizer specialized in Cambodian logistics documents and authentic Khmer script.
            
            STRICT SCRIPT RULES:
            - ALWAYS transcribe using authentic Khmer Unicode characters (\\u1780-\\u17FF) and standard Latin/ASCII characters for alphanumeric codes and timestamps.
            - STRICTLY FORBIDDEN: NEVER output Thai characters (\\u0E00-\\u0E7F) or Lao characters.
            - Vowels and subscript consonants (ជើង) must follow standard Khmer orthography.
            - Common Cambodian logistics branch names:
              * 'សាខា សៀមរាប' (Siem Reap, outlet code REP or REPDP01) - NEVER transcribe as 'เสีย', 'เสียហាប', or any Thai characters.
              * 'សាខា ភ្នំពេញ' (Phnom Penh, outlet code PNH or PNHDP)
              * 'សាខា បាត់ដំបង' (Battambang, outlet code BTB or BTBDP)
              * 'សាខា ព្រះសីហនុ' (Sihanoukville, outlet code KPS, SHV)
              * 'សាខា កំពង់ចាម' (Kampong Cham, outlet code KPC)
            """;

    public JsonResponse generateJson(byte[] imageBytes, String mimeType, String prompt, Map<String, Object> responseSchema) {
        String systemInstruction = null;
        if (prompt != null && (prompt.contains("Khmer") || prompt.contains("Cambodian") || prompt.contains("logistics"))) {
            systemInstruction = KHMER_LOGISTICS_SYSTEM_INSTRUCTION;
        }
        return generateJson(imageBytes, mimeType, prompt, responseSchema, systemInstruction);
    }

    /**
     * Calls Gemini Vision API with multimodal image data, structured JSON schema, and optional system instruction.
     * Retries transient 503 (high demand) and 429 errors with progressive backoff.
     */
    public JsonResponse generateJson(byte[] imageBytes, String mimeType, String prompt, Map<String, Object> responseSchema, String systemInstruction) {
        return generateJson(imageBytes, mimeType, prompt, responseSchema, systemInstruction, timeoutSeconds);
    }

    public JsonResponse generateJson(byte[] imageBytes, String mimeType, String prompt, Map<String, Object> responseSchema,
                                     String systemInstruction, int requestTimeoutSeconds) {
        return generateJson(List.of(new BarcodeImage(imageBytes, mimeType)), prompt, responseSchema,
                systemInstruction, requestTimeoutSeconds);
    }

    public JsonResponse generateJson(List<BarcodeImage> images, String prompt, Map<String, Object> responseSchema,
                                     String systemInstruction, int requestTimeoutSeconds) {
        String activeKey = (apiKey == null ? "" : apiKey.trim());
        if (activeKey.isEmpty()) {
            return new JsonResponse(401, "", "Gemini API key is missing or not configured.", null);
        }

        try {
            String requestJson = objectMapper.writeValueAsString(
                    buildRequestBody(images, prompt, responseSchema, systemInstruction, true));
            // Sent instead when a model rejects the media resolution setting
            String plainRequestJson = hasMediaResolution()
                    ? objectMapper.writeValueAsString(
                            buildRequestBody(images, prompt, responseSchema, systemInstruction, false))
                    : null;

            List<String> modelsToTry = new ArrayList<>();
            modelsToTry.add(getConfiguredModel());
            for (String fallback : FALLBACK_MODELS) {
                if (!modelsToTry.contains(fallback)) {
                    modelsToTry.add(fallback);
                }
            }

            // Models that recently reported their daily quota used up are not worth another request
            int quotaFailures = 0;
            int otherFailures = 0;
            List<String> usable = new ArrayList<>();
            for (String candidate : modelsToTry) {
                if (isQuotaBlocked(candidate)) {
                    quotaFailures++;
                } else {
                    usable.add(candidate);
                }
            }
            if (usable.isEmpty()) {
                return new JsonResponse(429, "",
                        "Gemini free-tier daily quota is used up for every configured model.", null, true);
            }
            List<String> answering = new ArrayList<>();
            List<String> busy = new ArrayList<>();
            for (String candidate : usable) {
                (isBusy(candidate) ? busy : answering).add(candidate);
            }
            answering.addAll(busy);
            modelsToTry = answering;

            HttpResponse<String> response = null;
            String usedModel = null;

            for (String targetModel : modelsToTry) {
                String targetUrl = String.format("%s/%s:generateContent", configuredBaseUrl(), targetModel);
                try {
                    response = httpClient.send(buildHttpRequest(targetUrl, activeKey, requestJson, requestTimeoutSeconds),
                            HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 400 && plainRequestJson != null) {
                        log.warn("Gemini model {} rejected media resolution {}. Retrying without it...",
                                targetModel, mediaResolution);
                        response = httpClient.send(buildHttpRequest(targetUrl, activeKey, plainRequestJson, requestTimeoutSeconds),
                                HttpResponse.BodyHandlers.ofString());
                    }
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        usedModel = targetModel;
                        busyUntil.remove(targetModel);
                        break;
                    }

                    if (isDailyQuotaError(response)) {
                        quotaFailures++;
                        quotaExhaustedUntil.put(targetModel, Instant.now().plus(QUOTA_RECHECK_INTERVAL));
                    } else {
                        otherFailures++;
                        if (isBusyStatus(response.statusCode())) {
                            markBusy(targetModel);
                        }
                    }

                    // On 503 (High demand) or 404/429, instantly failover to next model without wasting time retrying
                    if (modelsToTry.indexOf(targetModel) < modelsToTry.size() - 1) {
                        String fallbackModel = modelsToTry.get(modelsToTry.indexOf(targetModel) + 1);
                        log.warn("Gemini model {} unavailable (status {}). Instantly failing over to fallback model {}...",
                                targetModel, response.statusCode(), fallbackModel);
                    }
                } catch (HttpTimeoutException e) {
                    otherFailures++;
                    markBusy(targetModel);
                    if (modelsToTry.indexOf(targetModel) < modelsToTry.size() - 1) {
                        String fallbackModel = modelsToTry.get(modelsToTry.indexOf(targetModel) + 1);
                        log.warn("Gemini model {} timed out after {}s. Instantly failing over to fallback model {}...",
                                targetModel, requestTimeoutSeconds, fallbackModel);
                    } else {
                        log.warn("Gemini model {} timed out after {}s", targetModel, requestTimeoutSeconds);
                    }
                } catch (Exception e) {
                    otherFailures++;
                    log.warn("Gemini model {} request error: {}. Trying fallback...", targetModel, e.getMessage());
                }
            }

            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                return new JsonResponse(response != null ? response.statusCode() : 0, "",
                        response != null ? response.body() : "No response from Gemini API", usedModel,
                        quotaFailures > 0 && otherFailures == 0);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return new JsonResponse(response.statusCode(), "", "Gemini returned no candidates in response", usedModel);
            }

            JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
            String content = textNode.isMissingNode() ? "" : textNode.asText("").trim();
            if (content.isEmpty()) {
                return new JsonResponse(response.statusCode(), "", "Gemini returned an empty text response", usedModel);
            }

            return new JsonResponse(response.statusCode(), stripMarkdownFence(content), null, usedModel);
        } catch (Exception e) {
            log.error("Failed to call Gemini vision API: {}", e.getMessage());
            return new JsonResponse(0, "", e.getMessage(), null);
        }
    }

    Map<String, Object> buildRequestBody(byte[] imageBytes, String mimeType, String prompt,
                                         Map<String, Object> responseSchema, String systemInstruction,
                                         boolean includeMediaResolution) {
        return buildRequestBody(List.of(new BarcodeImage(imageBytes, mimeType)), prompt, responseSchema,
                systemInstruction, includeMediaResolution);
    }

    Map<String, Object> buildRequestBody(List<BarcodeImage> images, String prompt,
                                         Map<String, Object> responseSchema, String systemInstruction,
                                         boolean includeMediaResolution) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));
        for (int i = 0; i < images.size(); i++) {
            BarcodeImage image = images.get(i);
            String mimeType = image.mimeType();
            String resolvedMimeType = (mimeType == null || !mimeType.startsWith("image/")) ? "image/jpeg" : mimeType;
            if (images.size() > 1) {
                parts.add(Map.of("text", "Receipt " + (i + 1) + ":"));
            }
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", resolvedMimeType,
                    "data", Base64.getEncoder().encodeToString(image.imageBytes())
            )));
        }

        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", parts
        );

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.0);
        generationConfig.put("responseMimeType", "application/json");
        if (responseSchema != null && !responseSchema.isEmpty()) {
            generationConfig.put("responseSchema", responseSchema);
        }
        if (includeMediaResolution && hasMediaResolution()) {
            generationConfig.put("mediaResolution", mediaResolution.trim());
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            requestBody.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemInstruction))
            ));
        }
        requestBody.put("contents", List.of(userContent));
        requestBody.put("generationConfig", generationConfig);
        return requestBody;
    }

    private HttpRequest buildHttpRequest(String targetUrl, String apiKey, String requestJson, int requestTimeoutSeconds) {
        return HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(Math.max(1, requestTimeoutSeconds)))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();
    }

    private boolean isQuotaBlocked(String model) {
        Instant until = quotaExhaustedUntil.get(model);
        if (until == null) {
            return false;
        }
        if (Instant.now().isBefore(until)) {
            return true;
        }
        quotaExhaustedUntil.remove(model, until);
        return false;
    }

    private boolean isBusy(String model) {
        Instant until = busyUntil.get(model);
        if (until == null) {
            return false;
        }
        if (Instant.now().isBefore(until)) {
            return true;
        }
        busyUntil.remove(model, until);
        return false;
    }

    private void markBusy(String model) {
        busyUntil.put(model, Instant.now().plus(BUSY_COOLDOWN));
    }

    private static boolean isBusyStatus(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 503 || statusCode == 504;
    }

    /** A 429 for the per-day quota, which unlike a per-minute 429 does not clear within the next few minutes. */
    private static boolean isDailyQuotaError(HttpResponse<String> response) {
        return response.statusCode() == 429
                && response.body() != null
                && response.body().toLowerCase(Locale.ROOT).contains("perday");
    }

    private boolean hasMediaResolution() {
        return mediaResolution != null && !mediaResolution.isBlank();
    }

    public String getConfiguredModel() {
        return model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
    }

    private String configuredBaseUrl() {
        return baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
    }

    private List<String> parseBarcodeNumbers(String rawText) {
        Set<String> validatedCodes = new LinkedHashSet<>();
        try {
            JsonNode node = objectMapper.readTree(stripMarkdownFence(rawText));
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String val = item.asText("").trim();
                    if (!val.isEmpty()) {
                        validatedCodes.add(val);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Gemini barcode response parse fallback: {}", e.getMessage());
            String[] tokens = (rawText == null ? "" : rawText)
                    .replaceAll("[\\[\\]\\\"'{}]", "").split("[,\\n]+");
            for (String token : tokens) {
                String val = token.trim();
                if (!val.isEmpty()) {
                    validatedCodes.add(val);
                }
            }
        }
        return new ArrayList<>(validatedCodes);
    }

    private static String stripMarkdownFence(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
