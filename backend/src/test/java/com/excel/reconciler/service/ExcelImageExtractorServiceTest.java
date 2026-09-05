package com.excel.reconciler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelImageExtractorServiceTest {

    @Test
    void tableImageExtractionUsesLocalOllamaAndPreservesKhmerValues() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/chat", exchange -> {
            requests.incrementAndGet();
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200,
                    "{\"message\":{\"content\":\"{\\\"isExcelTable\\\":true,\\\"isBarcodeImage\\\":false,\\\"sheetName\\\":\\\"Inventory\\\",\\\"headers\\\":[\\\"Shipping Outlets\\\"],\\\"rows\\\":[{\\\"values\\\":[\\\"សាខាសៀមរាប/REPDP01\\\"]}]}\"}}");
        });
        server.start();

        try {
            OllamaVisionService ollama = new OllamaVisionService(new ObjectMapper());
            setField(ollama, "apiUrl", "http://localhost:" + server.getAddress().getPort() + "/api/chat");
            ExcelImageExtractorService service = new ExcelImageExtractorService(ollama, new ObjectMapper());

            var extracted = service.processExcelImage(
                    new MockMultipartFile("excelFile", "inventory.png", "image/png", new byte[]{1, 2, 3}));

            assertTrue(extracted.isExcelTable());
            assertEquals(List.of("Shipping Outlets"), extracted.getHeaders());
            assertEquals(1, extracted.getRows().size());
            assertEquals("សាខាសៀមរាប/REPDP01", extracted.getRows().get(0).get(0));
            assertEquals(1, requests.get());
            assertEquals("/api/chat", capturedPath.get());
            assertTrue(capturedBody.get().contains("qwen3-vl:8b-instruct"));
            assertTrue(capturedBody.get().contains("\"think\":false"));
            assertTrue(capturedBody.get().contains("Copy every visible cell exactly as displayed"));
            assertTrue(capturedBody.get().contains("Do not translate or infer"));
            assertTrue(capturedBody.get().contains("Use an empty string when a cell is genuinely blank or unreadable"));
            assertTrue(!capturedBody.get().contains("សៀមរាប"));
            assertTrue(capturedBody.get().contains("\"format\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void parseOllamaResponsePreservesRawWhitespaceInsideExtractedCells() {
        OllamaVisionService ollama = new OllamaVisionService(new ObjectMapper());
        ExcelImageExtractorService service = new ExcelImageExtractorService(ollama, new ObjectMapper());

        var extracted = service.parseOllamaResponse("""
                {
                  "isExcelTable": true,
                  "isBarcodeImage": false,
                  "headers": ["កូដ", "ឈ្មោះ"],
                  "rows": [{"values": ["  PROD-101  ", "  ទំនិញ  "]}]
                }
                """);

        assertEquals("  PROD-101  ", extracted.getRows().get(0).get(0));
        assertEquals("  ទំនិញ  ", extracted.getRows().get(0).get(1));
    }

    @Test
    void rejectsRowsWiderThanHeadersInsteadOfSilentlyDroppingCells() {
        OllamaVisionService ollama = new OllamaVisionService(new ObjectMapper());
        ExcelImageExtractorService service = new ExcelImageExtractorService(ollama, new ObjectMapper());

        var extracted = service.parseOllamaResponse("""
                {
                  "isExcelTable": true,
                  "isBarcodeImage": false,
                  "headers": ["កូដ", "ឈ្មោះ"],
                  "rows": [{"values": ["PROD-101", "ទំនិញ", "តម្លៃដែលបាត់ជួរ"]}]
                }
                """);

        assertEquals(null, extracted);
    }

    @Test
    void keepsRowsThatContainOnlyBlankCellsForPositionalIntegrity() {
        OllamaVisionService ollama = new OllamaVisionService(new ObjectMapper());
        ExcelImageExtractorService service = new ExcelImageExtractorService(ollama, new ObjectMapper());

        var extracted = service.parseOllamaResponse("""
                {
                  "isExcelTable": true,
                  "isBarcodeImage": false,
                  "headers": ["កូដ", "ឈ្មោះ"],
                  "rows": [{"values": ["", ""]}]
                }
                """);

        assertEquals(1, extracted.getRows().size());
        assertEquals(List.of("", ""), extracted.getRows().get(0));
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
