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
        assertEquals("gemini-3.5-flash-lite", service.getConfiguredModel());
    }

    private static GeminiVisionService spyAnswering(GeminiVisionService.JsonResponse response) {
        GeminiVisionService service = org.mockito.Mockito.spy(new GeminiVisionService(new ObjectMapper(), Runnable::run));
        org.mockito.Mockito.doReturn(response)
                .when(service).generateJson(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt());
        return service;
    }

    private static GeminiVisionService.BarcodeImage image(int b) {
        return new GeminiVisionService.BarcodeImage(new byte[]{(byte) b}, "image/jpeg");
    }

    @Test
    void receiptDateReadReturnsTheDateAndThePrintedWaybill() {
        GeminiVisionService service = spyAnswering(new GeminiVisionService.JsonResponse(200,
                "{\"receipts\":[{\"receipt\":1,\"createdText\":\" Oct 5, 2026, 04:14 PM \",\"waybillNo\":\"J01419454153\"}]}", null));

        var reading = service.extractReceiptCreatedText(new byte[]{1}, "image/jpeg");

        assertFalse(reading.failed());
        assertEquals("Oct 5, 2026, 04:14 PM", reading.text());
        assertEquals("J01419454153", reading.waybillNo());
    }

    @Test
    void receiptDateReadIsTriedOnceWithTheDateTimeout() {
        GeminiVisionService service = spyAnswering(new GeminiVisionService.JsonResponse(503, "", "busy"));

        var reading = service.extractReceiptCreatedText(new byte[]{1}, "image/jpeg");

        assertTrue(reading.failed());
        assertFalse(reading.quotaExhausted());
        org.mockito.Mockito.verify(service, org.mockito.Mockito.times(1)).generateJson(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(30));
    }

    @Test
    void receiptWithoutACreatedDateIsNotAFailure() {
        GeminiVisionService service = spyAnswering(new GeminiVisionService.JsonResponse(200,
                "{\"receipts\":[{\"receipt\":1,\"createdText\":\"\",\"waybillNo\":\"\"}]}", null));

        var reading = service.extractReceiptCreatedText(new byte[]{1}, "image/jpeg");

        assertFalse(reading.failed());
        assertEquals("", reading.text());
        assertEquals("", reading.waybillNo());
    }

    @Test
    void aBatchReadMatchesEachAnswerToItsReceiptNumberAndFailsTheMissingOnes() {
        GeminiVisionService service = spyAnswering(new GeminiVisionService.JsonResponse(200,
                "{\"receipts\":[{\"receipt\":3,\"createdText\":\"Oct 7, 2026\",\"waybillNo\":\"J3\"},"
                        + "{\"receipt\":1,\"createdText\":\"Oct 5, 2026\",\"waybillNo\":\"J1\"},"
                        + "{\"receipt\":9,\"createdText\":\"Oct 9, 2026\",\"waybillNo\":\"J9\"}]}", null));

        var readings = service.extractReceiptCreatedBatch(List.of(image(1), image(2), image(3)));

        assertEquals(3, readings.size());
        assertEquals("Oct 5, 2026", readings.get(0).text());
        assertEquals("J1", readings.get(0).waybillNo());
        assertTrue(readings.get(1).failed(), "receipt 2 was not answered");
        assertEquals("Oct 7, 2026", readings.get(2).text());
    }

    @Test
    void aFailedBatchRequestFailsEveryReceiptInIt() {
        GeminiVisionService service = spyAnswering(new GeminiVisionService.JsonResponse(503, "", "busy"));

        var readings = service.extractReceiptCreatedBatch(List.of(image(1), image(2)));

        assertEquals(2, readings.size());
        assertTrue(readings.stream().allMatch(GeminiVisionService.ReceiptDateRead::failed));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aBatchRequestLabelsEachReceiptImage() {
        GeminiVisionService service = new GeminiVisionService(new ObjectMapper(), Runnable::run);

        Map<String, Object> body = service.buildRequestBody(List.of(image(1), image(2)), "prompt", Map.of(), null, false);
        List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) contents.get(0).get("parts");

        assertEquals(List.of("prompt", "Receipt 1:", "Receipt 2:"), parts.stream()
                .filter(part -> part.containsKey("text")).map(part -> part.get("text")).toList());
        assertEquals(2, parts.stream().filter(part -> part.containsKey("inline_data")).count());
    }

    private static final String DAILY_QUOTA_BODY =
            "{\"error\":{\"code\":429,\"details\":[{\"violations\":[{\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}]}]}}";
    private static final String MINUTE_QUOTA_BODY =
            "{\"error\":{\"code\":429,\"details\":[{\"violations\":[{\"quotaId\":\"GenerateRequestsPerMinutePerProjectPerModel-FreeTier\"}]}]}}";

    /** Local stand-in for the Gemini API that answers every request with the given 429 body and counts them. */
    private static com.sun.net.httpserver.HttpServer apiAnswering429(String body, java.util.concurrent.atomic.AtomicInteger hits)
            throws java.io.IOException {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return server;
    }

    private static GeminiVisionService serviceAgainst(com.sun.net.httpserver.HttpServer server) {
        GeminiVisionService service = new GeminiVisionService(new ObjectMapper(), Runnable::run);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/models");
        return service;
    }

    @Test
    void dailyQuotaOnEveryModelIsFlaggedAndNotRetried() throws Exception {
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        var server = apiAnswering429(DAILY_QUOTA_BODY, hits);
        try {
            GeminiVisionService service = serviceAgainst(server);

            var first = service.extractReceiptCreatedText(new byte[]{1}, "image/jpeg");

            assertTrue(first.failed());
            assertTrue(first.quotaExhausted());
            assertEquals(2, hits.get());

            // Models known to be out of quota are skipped: no further requests at all
            var second = service.extractReceiptCreatedText(new byte[]{2}, "image/jpeg");
            assertTrue(second.quotaExhausted());
            assertEquals(2, hits.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void perMinuteLimitIsNotTreatedAsADailyQuota() throws Exception {
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        var server = apiAnswering429(MINUTE_QUOTA_BODY, hits);
        try {
            GeminiVisionService service = serviceAgainst(server);

            var reading = service.extractReceiptCreatedText(new byte[]{1}, "image/jpeg");

            assertTrue(reading.failed());
            assertFalse(reading.quotaExhausted());
            assertEquals(2, hits.get());
        } finally {
            server.stop(0);
        }
    }

    private static final String GEMINI_ANSWER_OCT_5;

    static {
        try {
            String answer = "{\"receipts\":[{\"receipt\":1,\"createdText\":\"Oct 5, 2026\",\"waybillNo\":\"\"}]}";
            GEMINI_ANSWER_OCT_5 = new ObjectMapper().writeValueAsString(Map.of("candidates", List.of(
                    Map.of("content", Map.of("parts", List.of(Map.of("text", answer)))))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aModelThatJustAnsweredBusyIsTriedLastByTheNextRequest() throws Exception {
        List<String> calledModels = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String model = path.substring(path.lastIndexOf('/') + 1, path.indexOf(':'));
            calledModels.add(model);
            boolean busy = model.equals("gemini-3.5-flash-lite");
            String body = busy ? "{\"error\":{\"code\":503}}" : GEMINI_ANSWER_OCT_5;
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(busy ? 503 : 200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            GeminiVisionService service = serviceAgainst(server);

            var first = service.extractReceiptCreatedText(new byte[]{1}, "image/jpeg");
            var second = service.extractReceiptCreatedText(new byte[]{2}, "image/jpeg");

            assertEquals("Oct 5, 2026", first.text());
            assertEquals("Oct 5, 2026", second.text());
            assertEquals(List.of("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.1-flash-lite"), calledModels);
        } finally {
            server.stop(0);
        }
    }
}
