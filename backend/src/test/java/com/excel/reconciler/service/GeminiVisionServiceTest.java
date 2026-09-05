package com.excel.reconciler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

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
    void configuredModelDefaultsToGeminiFlashLatest() {
        GeminiVisionService service = new GeminiVisionService(new ObjectMapper(), Runnable::run);
        assertEquals("gemini-flash-latest", service.getConfiguredModel());
    }
}
