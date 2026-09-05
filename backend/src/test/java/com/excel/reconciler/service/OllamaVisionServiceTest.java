package com.excel.reconciler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaVisionServiceTest {

    @Test
    void preservesAlphanumericBarcodePayloads() throws Exception {
        OllamaVisionService service = new OllamaVisionService(new ObjectMapper());
        Method parser = OllamaVisionService.class.getDeclaredMethod("parseBarcodeNumbers", String.class);
        parser.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) parser.invoke(service, "[\"J01406053981\"]");

        assertEquals(List.of("J01406053981"), values);
    }

    @Test
    void keepsBatchBarcodeResultsMappedToTheirImageIndexes() throws Exception {
        OllamaVisionService service = new OllamaVisionService(new ObjectMapper());
        Method parser = OllamaVisionService.class.getDeclaredMethod(
                "parseBarcodeBatch", String.class, int.class);
        parser.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<List<String>> values = (List<List<String>>) parser.invoke(service,
                """
                {
                  "results": [
                    {"imageIndex": 1, "values": ["J01406053981"]},
                    {"imageIndex": 0, "values": ["0718368902"]}
                  ]
                }
                """, 2);

        assertEquals(List.of(List.of("0718368902"), List.of("J01406053981")), values);
    }

    @Test
    void barcodeExtractionUsesLocalQwenVisionModelAndReturnsStructuredValues() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/chat", exchange -> {
            requests.incrementAndGet();
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200,
                    "{\"message\":{\"content\":\"[\\\"01400310465\\\"]\"}}");
        });
        server.start();

        try {
            OllamaVisionService service = new OllamaVisionService(new ObjectMapper());
            setField(service, "apiUrl", "http://localhost:" + server.getAddress().getPort() + "/api/chat");

            assertEquals(List.of("01400310465"), service.extractBarcodesWithOllama(
                    new byte[]{1, 2, 3}, "image/png"));
            assertEquals(1, requests.get());
            assertEquals("/api/chat", capturedPath.get());
            assertTrue(capturedBody.get().contains("qwen3-vl:8b-instruct"));
            assertTrue(capturedBody.get().contains("\"think\":false"));
            assertTrue(capturedBody.get().contains("\"images\""));
            assertTrue(capturedBody.get().contains("\"format\""));
        } finally {
            server.stop(0);
        }
    }

    private static void setField(OllamaVisionService service, String name, String value) throws Exception {
        var field = OllamaVisionService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
