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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class GeminiVisionService {
    private static final Logger log = LoggerFactory.getLogger(GeminiVisionService.class);
    private static final String DEFAULT_MODEL = "gemini-flash-latest";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-flash-latest}")
    private String model = DEFAULT_MODEL;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String baseUrl = DEFAULT_BASE_URL;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public record BarcodeImage(byte[] imageBytes, String mimeType) {
    }

    public record JsonResponse(int statusCode, String content, String errorMessage, String usedModel) {
        public JsonResponse(int statusCode, String content, String errorMessage) {
            this(statusCode, content, errorMessage, null);
        }

        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300 && content != null && !content.isBlank();
        }
    }

    public GeminiVisionService(ObjectMapper objectMapper,
                               @Qualifier("imageDecoderExecutor") Executor executor) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
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
        String activeKey = (apiKey == null ? "" : apiKey.trim());
        if (activeKey.isEmpty()) {
            return new JsonResponse(401, "", "Gemini API key is missing or not configured.", null);
        }

        try {
            String resolvedMimeType = (mimeType == null || !mimeType.startsWith("image/")) ? "image/jpeg" : mimeType;
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> textPart = Map.of("text", prompt);
            Map<String, Object> inlineData = Map.of(
                    "mime_type", resolvedMimeType,
                    "data", base64Image
            );
            Map<String, Object> imagePart = Map.of("inline_data", inlineData);

            Map<String, Object> userContent = Map.of(
                    "role", "user",
                    "parts", List.of(textPart, imagePart)
            );

            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("temperature", 0.0);
            generationConfig.put("responseMimeType", "application/json");
            if (responseSchema != null && !responseSchema.isEmpty()) {
                generationConfig.put("responseSchema", responseSchema);
            }

            Map<String, Object> requestBody = new LinkedHashMap<>();
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                requestBody.put("systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ));
            }
            requestBody.put("contents", List.of(userContent));
            requestBody.put("generationConfig", generationConfig);

            String requestJson = objectMapper.writeValueAsString(requestBody);

            String configured = getConfiguredModel();
            List<String> modelsToTry = new ArrayList<>();
            modelsToTry.add(configured);
            if (!modelsToTry.contains("gemini-flash-latest")) {
                modelsToTry.add("gemini-flash-latest");
            }
            if (!modelsToTry.contains("gemini-flash-lite-latest")) {
                modelsToTry.add("gemini-flash-lite-latest");
            }

            HttpResponse<String> response = null;
            String usedModel = null;

            for (String targetModel : modelsToTry) {
                String targetUrl = String.format("%s/%s:generateContent", configuredBaseUrl(), targetModel);
                int maxAttempts = modelsToTry.indexOf(targetModel) == 0 ? 2 : 1;
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(targetUrl))
                            .header("Content-Type", "application/json")
                            .header("x-goog-api-key", activeKey)
                            .timeout(Duration.ofMinutes(2))
                            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                            .build();

                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        usedModel = targetModel;
                        break;
                    }

                    if ((response.statusCode() == 503 || response.statusCode() == 429) && attempt < maxAttempts) {
                        long backoff = attempt * 1200L;
                        log.warn("Gemini model {} returned status {}. Retrying attempt {}/{} in {}ms...",
                                targetModel, response.statusCode(), attempt + 1, maxAttempts, backoff);
                        try {
                            Thread.sleep(backoff);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    break;
                }

                if (response != null && response.statusCode() >= 200 && response.statusCode() < 300) {
                    break;
                }

                if (modelsToTry.indexOf(targetModel) < modelsToTry.size() - 1) {
                    String fallbackModel = modelsToTry.get(modelsToTry.indexOf(targetModel) + 1);
                    log.warn("Gemini model {} unavailable (status {}). Switching to fallback model {}...",
                            targetModel, response != null ? response.statusCode() : 0, fallbackModel);
                }
            }

            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                return new JsonResponse(response != null ? response.statusCode() : 0, "",
                        response != null ? response.body() : "No response from Gemini API", usedModel);
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
