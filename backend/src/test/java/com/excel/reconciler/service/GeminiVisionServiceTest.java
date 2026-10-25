package com.excel.reconciler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiVisionServiceTest {

    @Test
    void preservesAlphanumericBarcodePayloads() throws Exception {
        GeminiVisionService service = new GeminiVisionService(new ObjectMapper(), Runnable::run);
        Method parser = GeminiVisionService.class.getDeclaredMethod("parseBarcodeNumbers", String.class);
        parser.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) parser.invoke(service, "[\"J01406053981\", \"0718368902\"]");

        assertEquals(List.of("J01406053981", "0718368902"), values);
    }

    @Test
    void parseBarcodeNumbersHandlesMarkdownFenceAndFallback() throws Exception {
        GeminiVisionService service = new GeminiVisionService(new ObjectMapper(), Runnable::run);
        Method parser = GeminiVisionService.class.getDeclaredMethod("parseBarcodeNumbers", String.class);
        parser.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) parser.invoke(service, "```json\n[\"840192837465\"]\n```");

        assertEquals(List.of("840192837465"), values);
    }

    @Test
    void emptyOrNullImagesReturnEmptyList() {
        GeminiVisionService service = new GeminiVisionService(new ObjectMapper(), Runnable::run);
        assertEquals(Collections.emptyList(), service.extractBarcodesWithGemini(null, "image/png"));
        assertEquals(Collections.emptyList(), service.extractBarcodesWithGemini(new byte[0], "image/png"));
        assertEquals(Collections.emptyList(), service.extractBarcodesParallel(null));
        assertEquals(Collections.emptyList(), service.extractBarcodesParallel(Collections.emptyList()));
    }

    @Test
    void returns401WhenApiKeyIsMissing() {
        GeminiVisionService service = new GeminiVisionService(new ObjectMapper(), Runnable::run);
        var response = service.generateJson(new byte[]{1, 2, 3}, "image/png", "Test prompt", Collections.emptyMap());
        assertEquals(401, response.statusCode());
        assertFalse(response.isSuccessful());
        assertTrue(response.errorMessage().contains("Gemini API key is missing"));
    }

    @Test
    void requestAsksForConfiguredMediaResolutionUnlessDisabledForRetry() {
        GeminiVisionService service = new GeminiVisionService(new ObjectMapper(), Runnable::run);
        ReflectionTestUtils.setField(service, "mediaResolution", "MEDIA_RESOLUTION_HIGH");

        assertEquals("MEDIA_RESOLUTION_HIGH",
                generationConfig(service.buildRequestBody(new byte[]{1}, "image/png", "p", Map.of(), null, true))
                        .get("mediaResolution"));
        assertFalse(generationConfig(service.buildRequestBody(new byte[]{1}, "image/png", "p", Map.of(), null, false))
                .containsKey("mediaResolution"));
    }

    @Test
    void requestOmitsMediaResolutionWhenNotConfigured() {
        GeminiVisionService service = new GeminiVisionService(new ObjectMapper(), Runnable::run);

        assertFalse(generationConfig(service.buildRequestBody(new byte[]{1}, "image/png", "p", Map.of(), null, true))
                .containsKey("mediaResolution"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> generationConfig(Map<String, Object> requestBody) {
        return (Map<String, Object>) requestBody.get("generationConfig");
    }

    @Test
    void configuredModelDefaultsToGeminiFlashLite() {
        GeminiVisionService service = new GeminiVisionService(new ObjectMapper(), Runnable::run);
        assertEquals("gemini-flash-lite-latest", service.getConfiguredModel());
    }

    @Test
    void receiptDateReadIsRetriedAfterAFailedRequestAndSucceeds() {
        GeminiVisionService service = org.mockito.Mockito.spy(new GeminiVisionService(new ObjectMapper(), Runnable::run));
        ReflectionTestUtils.setField(service, "dateReadRetryDelayMs", 0L);
        org.mockito.Mockito.doReturn(
                        new GeminiVisionService.JsonResponse(429, "", "rate limited"),
                        new GeminiVisionService.JsonResponse(200, "{\"createdText\":\"Oct 5, 2026, 04:14 PM\"}", null))
                .when(service).generateJson(org.mockito.ArgumentMatchers.any(byte[].class), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any());

        var reading = service.extractReceiptCreatedText(new byte[]{1}, "image/jpeg");

        assertFalse(reading.failed());
        assertEquals("Oct 5, 2026, 04:14 PM", reading.text());
    }

    @Test
    void receiptDateReadReportsFailureAfterAllAttemptsInsteadOfNoDate() {
        GeminiVisionService service = org.mockito.Mockito.spy(new GeminiVisionService(new ObjectMapper(), Runnable::run));
        ReflectionTestUtils.setField(service, "dateReadRetryDelayMs", 0L);
        org.mockito.Mockito.doReturn(new GeminiVisionService.JsonResponse(503, "", "busy"))
                .when(service).generateJson(org.mockito.ArgumentMatchers.any(byte[].class), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any());

        var reading = service.extractReceiptCreatedText(new byte[]{1}, "image/jpeg");

        assertTrue(reading.failed());
        org.mockito.Mockito.verify(service, org.mockito.Mockito.times(3)).generateJson(
                org.mockito.ArgumentMatchers.any(byte[].class), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void receiptWithoutACreatedDateIsNotAFailure() {
        GeminiVisionService service = org.mockito.Mockito.spy(new GeminiVisionService(new ObjectMapper(), Runnable::run));
        org.mockito.Mockito.doReturn(new GeminiVisionService.JsonResponse(200, "{\"createdText\":\"\"}", null))
                .when(service).generateJson(org.mockito.ArgumentMatchers.any(byte[].class), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any());

        var reading = service.extractReceiptCreatedText(new byte[]{1}, "image/jpeg");

        assertFalse(reading.failed());
        assertEquals("", reading.text());
    }
}
