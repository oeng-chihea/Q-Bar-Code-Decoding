package com.excel.reconciler.service;

import com.excel.reconciler.util.BarcodeValidator;
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
import java.util.*;

@Service
public class GeminiVisionService {
    private static final Logger log = LoggerFactory.getLogger(GeminiVisionService.class);

    @Value("${gemini.api.key:}")
    private String defaultApiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String baseUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Strictly Gemini 3.5 and 3.1 models
    private final List<String> candidateModels = List.of(
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview",
            "gemini-3.1-flash",
            "gemini-3.1-pro"
    );

    public GeminiVisionService(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = objectMapper;
    }

    public List<String> extractBarcodesWithGemini(byte[] imageBytes, String mimeType, String customApiKey) {
        String apiKey = (customApiKey != null && !customApiKey.trim().isEmpty()) ? customApiKey.trim() : defaultApiKey;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.info("Gemini API key not provided; skipping AI vision fallback.");
            return Collections.emptyList();
        }

        try {
            String base64Data = Base64.getEncoder().encodeToString(imageBytes);
            if (mimeType == null || mimeType.isEmpty() || !mimeType.startsWith("image/")) {
                mimeType = "image/jpeg";
            }

            String prompt = "You are an optical barcode and QR code reader. "
                    + "Scan this image and extract ONLY the exact Barcode and QR code numbers (e.g. '840192837401', '719283049581'). "
                    + "For each barcode or QR code visible (whether 1 product or a multi-barcode sheet): "
                    + "Extract the full numeric barcode digits. Do NOT extract Item IDs, product names, categories, or descriptions. "
                    + "Return strictly a JSON array of strings containing only the barcode numbers found, for example: [\"840192837401\", \"840192837418\"]. "
                    + "If no barcodes are found, return [].";

            Map<String, Object> inlineData = Map.of(
                    "mime_type", mimeType,
                    "data", base64Data
            );

            Map<String, Object> textPart = Map.of("text", prompt);
            Map<String, Object> imagePart = Map.of("inline_data", inlineData);

            Map<String, Object> contentObj = Map.of(
                    "parts", List.of(textPart, imagePart)
            );

            // Strict JSON Schema for pure string array of barcode numbers
            Map<String, Object> schema = Map.of(
                    "type", "ARRAY",
                    "description", "List of detected barcode or QR code numbers",
                    "items", Map.of(
                            "type", "STRING",
                            "description", "Numeric barcode digits (e.g. 840192837401) or QR code content"
                    )
            );

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(contentObj),
                    "generationConfig", Map.of(
                            "temperature", 0.0,
                            "responseMimeType", "application/json",
                            "responseSchema", schema
                    )
            );

            String requestJson = objectMapper.writeValueAsString(requestBody);

            for (String model : candidateModels) {
                String url = String.format("%s/%s:generateContent?key=%s", baseUrl, model, apiKey);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode rootNode = objectMapper.readTree(response.body());
                    JsonNode candidates = rootNode.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
                        if (!textNode.isMissingNode()) {
                            String rawText = textNode.asText().trim();
                            List<String> parsed = parseBarcodeNumbers(rawText);
                            if (!parsed.isEmpty()) {
                                log.info("Gemini {} successfully extracted {} barcode numbers: {}", model, parsed.size(), parsed);
                                return parsed;
                            }
                        }
                    }
                } else {
                    log.warn("Gemini model {} returned status {}: {}", model, response.statusCode(), response.body());
                }
            }
        } catch (Exception e) {
            log.error("Failed in Gemini Vision extraction: {}", e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    private List<String> parseBarcodeNumbers(String rawText) {
        Set<String> validatedCodes = new LinkedHashSet<>();
        try {
            String cleanJson = rawText;
            if (cleanJson.startsWith("```")) {
                int firstNewline = cleanJson.indexOf('\n');
                int lastBackticks = cleanJson.lastIndexOf("```");
                if (firstNewline != -1 && lastBackticks > firstNewline) {
                    cleanJson = cleanJson.substring(firstNewline + 1, lastBackticks).trim();
                }
            }

            JsonNode node = objectMapper.readTree(cleanJson);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String val = item.isTextual() ? item.asText().trim() : item.isObject() ? item.path("barcode").asText("").trim() : item.asText().trim();
                    if (!val.isEmpty()) {
                        String clean = BarcodeValidator.cleanCode(val);
                        if (clean.matches("^\\d{4,30}$") || BarcodeValidator.isValidBarcode(clean)) {
                            validatedCodes.add(clean);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Barcode parse fallback: {}", e.getMessage());
            String[] tokens = rawText.replaceAll("[\\[\\]\"'{}]", "").split("[,\\n]+");
            for (String t : tokens) {
                String clean = BarcodeValidator.cleanCode(t);
                if (clean.matches("^\\d{4,30}$")) {
                    validatedCodes.add(clean);
                }
            }
        }
        return new ArrayList<>(validatedCodes);
    }
}
