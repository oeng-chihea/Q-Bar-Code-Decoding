package com.excel.reconciler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service
public class OllamaVisionService {
    private static final Logger log = LoggerFactory.getLogger(OllamaVisionService.class);
    private static final String DEFAULT_MODEL = "qwen3-vl:8b-instruct";

    @Value("${ollama.api.url:http://localhost:11434/api/chat}")
    private String apiUrl;

    @Value("${ollama.api.model:qwen3-vl:8b-instruct}")
    private String preferredModel = DEFAULT_MODEL;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public record BarcodeImage(byte[] imageBytes, String mimeType) {
    }

    public OllamaVisionService(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    public List<String> extractBarcodesWithOllama(byte[] imageBytes, String mimeType) {
        String prompt = """
                You are an expert barcode and QR code reader for inventory reconciliation.
                Inspect the image carefully, including rotated, tilted, blurry, or partially obscured labels.

                Extract ONLY barcode or QR payloads that are actually encoded in the image.
                Return each payload exactly as encoded, including letters, leading zeros, and hyphens.
                Do not return printed text, product names, SKU descriptions, Khmer text, outlet names, prices, phone numbers, or unrelated numbers.
                Return a JSON array of strings. If no barcode is visible, return an empty array.
                """;

        Map<String, Object> schema = Map.of(
                "type", "array",
                "items", Map.of("type", "string")
        );

        JsonResponse response = generateJson(imageBytes, mimeType, prompt, schema);
        if (!response.isSuccessful()) {
            log.warn("Ollama barcode extraction failed with status {}: {}",
                    response.statusCode(), response.errorMessage());
            return Collections.emptyList();
        }

        List<String> parsed = parseBarcodeNumbers(response.content());
        if (!parsed.isEmpty()) {
            log.info("Ollama {} successfully extracted {} codes: {}",
                    configuredModel(), parsed.size(), parsed);
        }
        return parsed;
    }

    /**
     * Processes only images that failed local ZXing decoding. The returned list is aligned with
     * the input list so the caller can preserve the original upload order.
     */
    public List<List<String>> extractBarcodesWithOllamaBatch(List<BarcodeImage> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }

        String prompt = """
                You are an expert barcode and QR code reader for inventory reconciliation.
                You are given multiple images in order, starting with image index 0.

                For every image, extract ONLY barcode or QR payloads that are actually encoded in that image.
                Return each payload exactly as encoded, including letters, leading zeros, and hyphens.
                Do not return printed text, product names, SKU descriptions, Khmer text, outlet names, prices, phone numbers, or unrelated numbers.
                Return one result object for each image. If an image has no visible barcode or QR code, return an empty values array for that image.
                """;

        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "results", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "imageIndex", Map.of("type", "integer"),
                                                "values", Map.of("type", "array", "items", Map.of("type", "string"))
                                        ),
                                        "required", List.of("imageIndex", "values")
                                )
                        )
                ),
                "required", List.of("results")
        );

        List<byte[]> imageBytes = images.stream().map(BarcodeImage::imageBytes).toList();
        JsonResponse response = generateJson(imageBytes, prompt, schema, 128);
        if (!response.isSuccessful()) {
            log.warn("Ollama barcode batch extraction failed with status {}: {}",
                    response.statusCode(), response.errorMessage());
            return emptyBatch(images.size());
        }

        List<List<String>> parsed = parseBarcodeBatch(response.content(), images.size());
        log.info("Ollama {} processed {} barcode fallback image(s) in one request",
                configuredModel(), images.size());
        return parsed;
    }

    public JsonResponse generateJson(byte[] imageBytes, String mimeType, String prompt,
                                     Map<String, Object> responseSchema) {
        return generateJson(Collections.singletonList(imageBytes), prompt, responseSchema, null);
    }

    private JsonResponse generateJson(List<byte[]> imageBytes, String prompt,
                                      Map<String, Object> responseSchema, Integer maxOutputTokens) {
        if (imageBytes == null || imageBytes.isEmpty()
                || imageBytes.stream().anyMatch(bytes -> bytes == null || bytes.length == 0)) {
            return new JsonResponse(0, "", "Image data is empty");
        }

        try {
            List<String> base64Images = imageBytes.stream()
                    .map(bytes -> Base64.getEncoder().encodeToString(bytes))
                    .toList();
            Map<String, Object> message = Map.of(
                    "role", "user",
                    "content", prompt,
                    "images", base64Images
            );
            Map<String, Object> options = maxOutputTokens == null
                    ? Map.of("temperature", 0.0)
                    : Map.of("temperature", 0.0, "num_predict", maxOutputTokens);
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", configuredModel());
            requestBody.put("messages", List.of(message));
            requestBody.put("stream", false);
            requestBody.put("think", false);
            requestBody.put("format", responseSchema);
            requestBody.put("options", options);
            requestBody.put("keep_alive", "10m");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(configuredApiUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(3))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new JsonResponse(response.statusCode(), "", response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("message").path("content").asText("").trim();
            if (content.isEmpty()) {
                return new JsonResponse(response.statusCode(), "", "Ollama returned an empty response");
            }
            return new JsonResponse(response.statusCode(), content, null);
        } catch (Exception e) {
            log.error("Failed to call local Ollama vision model: {}", e.getMessage());
            return new JsonResponse(0, "", e.getMessage());
        }
    }

    public String getConfiguredModel() {
        return configuredModel();
    }

    private String configuredApiUrl() {
        return apiUrl == null || apiUrl.isBlank() ? "http://localhost:11434/api/chat" : apiUrl.trim();
    }

    private String configuredModel() {
        return preferredModel == null || preferredModel.isBlank() ? DEFAULT_MODEL : preferredModel.trim();
    }

    private List<String> parseBarcodeNumbers(String rawText) {
        Set<String> validatedCodes = new LinkedHashSet<>();
        try {
            JsonNode node = objectMapper.readTree(stripMarkdownFence(rawText));
            if (node.isArray()) {
                validatedCodes.addAll(parseBarcodeValues(node));
            }
        } catch (Exception e) {
            log.debug("Ollama barcode response parse fallback: {}", e.getMessage());
            String[] tokens = (rawText == null ? "" : rawText)
                    .replaceAll("[\\[\\]\\\"'{}]", "").split("[,\\n]+");
            for (String token : tokens) {
                String value = validateBarcodeValue(token);
                if (value != null) {
                    validatedCodes.add(value);
                }
            }
        }
        return new ArrayList<>(validatedCodes);
    }

    private List<List<String>> parseBarcodeBatch(String rawText, int imageCount) {
        List<List<String>> valuesByImage = new ArrayList<>();
        for (int i = 0; i < imageCount; i++) {
            valuesByImage.add(new ArrayList<>());
        }

        try {
            JsonNode root = objectMapper.readTree(stripMarkdownFence(rawText));
            JsonNode results = root.isObject() ? root.path("results") : root;
            if (results.isArray()) {
                for (JsonNode item : results) {
                    if (item.isObject()) {
                        int imageIndex = item.path("imageIndex").asInt(-1);
                        if (imageIndex >= 0 && imageIndex < imageCount) {
                            valuesByImage.set(imageIndex, parseBarcodeValues(item.path("values")));
                        }
                    } else if (imageCount == 1) {
                        valuesByImage.set(0, parseBarcodeValues(results));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Ollama barcode batch response parse failed: {}", e.getMessage());
        }
        return valuesByImage;
    }

    private List<String> parseBarcodeValues(JsonNode valuesNode) {
        Set<String> values = new LinkedHashSet<>();
        if (valuesNode != null && valuesNode.isArray()) {
            for (JsonNode item : valuesNode) {
                String raw = item.isTextual()
                        ? item.asText()
                        : item.isObject() ? item.path("barcode").asText("") : item.asText("");
                String value = validateBarcodeValue(raw);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return new ArrayList<>(values);
    }

    private String validateBarcodeValue(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < 4 || value.length() > 50) {
            return null;
        }
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_./:#-]*$")) {
            return null;
        }
        return com.excel.reconciler.util.BarcodeValidator.isValidCode(value) ? value : null;
    }

    private List<List<String>> emptyBatch(int imageCount) {
        List<List<String>> empty = new ArrayList<>();
        for (int i = 0; i < imageCount; i++) {
            empty.add(Collections.emptyList());
        }
        return empty;
    }

    private String stripMarkdownFence(String rawText) {
        String clean = rawText == null ? "" : rawText.trim();
        if (clean.startsWith("```")) {
            int firstNewline = clean.indexOf('\n');
            int lastBackticks = clean.lastIndexOf("```");
            if (firstNewline != -1 && lastBackticks > firstNewline) {
                return clean.substring(firstNewline + 1, lastBackticks).trim();
            }
        }
        return clean;
    }

    public record JsonResponse(int statusCode, String content, String errorMessage) {
        public boolean isSuccessful() {
            return statusCode == 200 && content != null && !content.isBlank();
        }
    }
}
