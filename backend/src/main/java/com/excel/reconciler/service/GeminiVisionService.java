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

            String prompt = "You are an expert warehouse barcode scanner. Extract ALL barcodes, QR codes, and product identifiers from this image: "
                    + "1. 1D Barcode digits (look directly below the barcode lines, e.g. '840192837465', '719283049581') "
                    + "2. QR Code content "
                    + "3. SKU / Model numbers (e.g. 'TL-9042-X', 'HP-5510-B') "
                    + "Return strictly a JSON array of strings containing all numbers and SKU codes found, e.g. [\"840192837465\", \"TL-9042-X\"]. "
                    + "If none, return [].";

            Map<String, Object> inlineData = Map.of(
                    "mime_type", mimeType,
                    "data", base64Data
            );

            Map<String, Object> textPart = Map.of("text", prompt);
            Map<String, Object> imagePart = Map.of("inline_data", inlineData);

            Map<String, Object> contentObj = Map.of(
                    "parts", List.of(textPart, imagePart)
            );

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(contentObj),
                    "generationConfig", Map.of(
                            "temperature", 0.1,
                            "responseMimeType", "application/json"
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
                            List<String> parsed = parseJsonArray(rawText);
                            if (!parsed.isEmpty()) {
                                log.info("Gemini model {} successfully extracted barcodes: {}", model, parsed);
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

    private List<String> parseJsonArray(String rawText) {
        List<String> results = new ArrayList<>();
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
                    if (item.isTextual() && !item.asText().trim().isEmpty()) {
                        results.add(item.asText().trim());
                    } else if (item.isNumber()) {
                        results.add(item.asText());
                    }
                }
            }
        } catch (Exception e) {
            String[] tokens = rawText.replaceAll("[\\[\\]\"'{}]", "").split("[,\\n]+");
            for (String t : tokens) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    results.add(trimmed);
                }
            }
        }
        return results;
    }
}
