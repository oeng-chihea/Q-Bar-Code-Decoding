package com.excel.reconciler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcelImageExtractorServiceTest {

    @Test
    void tableImageExtractionUsesGeminiAndPreservesKhmerValues() throws Exception {
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        when(gemini.generateJson(any(), any(), any(), any())).thenReturn(
                new GeminiVisionService.JsonResponse(200,
                        "{\"isExcelTable\":true,\"isBarcodeImage\":false,\"sheetName\":\"Inventory\",\"headers\":[\"Shipping Outlets\"],\"rows\":[{\"values\":[\"សាខាសៀមរាប/REPDP01\"]}]}",
                        null));
        when(gemini.getConfiguredModel()).thenReturn("gemini-2.5-flash");

        ExcelImageExtractorService service = new ExcelImageExtractorService(gemini, new ObjectMapper());

        var extracted = service.processExcelImage(
                new MockMultipartFile("excelFile", "inventory.png", "image/png", new byte[]{1, 2, 3}));

        assertTrue(extracted.isExcelTable());
        assertEquals(List.of("Shipping Outlets"), extracted.getHeaders());
        assertEquals(1, extracted.getRows().size());
        assertEquals("សាខាសៀមរាប/REPDP01", extracted.getRows().get(0).get(0));
    }

    @Test
    void preprocessTableImageUpscalesLowResolutionImages() throws Exception {
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ExcelImageExtractorService service = new ExcelImageExtractorService(gemini, new ObjectMapper());

        // Test with invalid bytes (graceful fallback)
        byte[] dummy = new byte[]{1, 2, 3};
        assertEquals(dummy, service.preprocessTableImage(dummy, "image/png"));

        // Test with a small valid 200x100 PNG image
        java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(200, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(small, "png", baos);
        byte[] originalBytes = baos.toByteArray();

        byte[] upscaledBytes = service.preprocessTableImage(originalBytes, "image/png");
        assertTrue(upscaledBytes.length > 0);

        java.awt.image.BufferedImage upscaled = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(upscaledBytes));
        assertNotNull(upscaled);
        // Should be upscaled by max scale 2.5x: 200*2.5 = 500, 100*2.5 = 250
        assertEquals(500, upscaled.getWidth());
        assertEquals(250, upscaled.getHeight());
    }

    @Test
    void parseGeminiResponsePreservesRawWhitespaceInsideExtractedCells() {
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ExcelImageExtractorService service = new ExcelImageExtractorService(gemini, new ObjectMapper());

        var extracted = service.parseGeminiResponse("""
                {
                  "isExcelTable": true,
                  "isBarcodeImage": false,
                  "headers": ["កូដ", "ឈ្មោះ"],
                  "rows": [{"values": ["  PROD-101  ", "  ទំនិញ  "]}]
                }
                """);

        assertNotNull(extracted);
        assertEquals("  PROD-101  ", extracted.getRows().get(0).get(0));
        assertEquals("  ទំនិញ  ", extracted.getRows().get(0).get(1));
    }

    @Test
    void trimsRowsWiderThanHeadersAndPadsShorterRowsLeniently() {
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ExcelImageExtractorService service = new ExcelImageExtractorService(gemini, new ObjectMapper());

        var extracted = service.parseGeminiResponse("""
                {
                  "isExcelTable": true,
                  "isBarcodeImage": false,
                  "headers": ["កូដ", "ឈ្មោះ"],
                  "rows": [
                    {"values": ["PROD-101", "ទំនិញ", "extra_cell"]},
                    {"values": ["PROD-102"]}
                  ]
                }
                """);

        assertNotNull(extracted);
        assertEquals(2, extracted.getRows().size());
        assertEquals(List.of("PROD-101", "ទំនិញ"), extracted.getRows().get(0));
        assertEquals(List.of("PROD-102", ""), extracted.getRows().get(1));
    }

    @Test
    void handlesNullCellValuesGracefully() {
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ExcelImageExtractorService service = new ExcelImageExtractorService(gemini, new ObjectMapper());

        var extracted = service.parseGeminiResponse("""
                {
                  "isExcelTable": true,
                  "isBarcodeImage": false,
                  "headers": ["កូដ", "ឈ្មោះ"],
                  "rows": [{"values": ["PROD-101", null]}]
                }
                """);

        assertNotNull(extracted);
        assertEquals(List.of("PROD-101", ""), extracted.getRows().get(0));
    }

    @Test
    void keepsRowsThatContainOnlyBlankCellsForPositionalIntegrity() {
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ExcelImageExtractorService service = new ExcelImageExtractorService(gemini, new ObjectMapper());

        var extracted = service.parseGeminiResponse("""
                {
                  "isExcelTable": true,
                  "isBarcodeImage": false,
                  "headers": ["កូដ", "ឈ្មោះ"],
                  "rows": [{"values": ["", ""]}]
                }
                """);

        assertNotNull(extracted);
        assertEquals(1, extracted.getRows().size());
        assertEquals(List.of("", ""), extracted.getRows().get(0));
    }

    @Test
    void sanitizesThaiScriptAndNormalizesCambodianLogisticsBranches() {
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ExcelImageExtractorService service = new ExcelImageExtractorService(gemini, new ObjectMapper());

        // 1. Thai hallucination for Siem Reap
        assertEquals("សាខា សៀមរាប/REPDP01", service.sanitizeKhmerLogisticsText("សាខា เสียហាប/REPDP01"));
        assertEquals("សៀមរាប/REPDP01", service.sanitizeKhmerLogisticsText("เสียហាប/REPDP01"));
        assertEquals("សាខា សៀមរាប/REPDP01", service.sanitizeKhmerLogisticsText("សាខា សៀមរាប/REPDP01"));

        // 2. Phnom Penh branch
        assertEquals("សាខា ភ្នំពេញ/PNH01", service.sanitizeKhmerLogisticsText("សាខា ភ្នំពេញ/PNH01"));

        // 3. Battambang branch
        assertEquals("សាខា បាត់ដំបង/BTB01", service.sanitizeKhmerLogisticsText("សាខា/BTB01"));

        // 4. Other cells unchanged
        assertEquals("2026-09-01 09:19:48", service.sanitizeKhmerLogisticsText("2026-09-01 09:19:48"));
        assertEquals("26,000", service.sanitizeKhmerLogisticsText("26,000"));
    }

    @Test
    void parseGeminiResponseAutomaticallySanitizesRowsAndHeaders() {
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ExcelImageExtractorService service = new ExcelImageExtractorService(gemini, new ObjectMapper());

        var extracted = service.parseGeminiResponse("""
                {
                  "isExcelTable": true,
                  "isBarcodeImage": false,
                  "headers": ["សាខា/Code", "តម្លៃ"],
                  "rows": [
                    {"values": ["សាខា เสียហាប/REPDP01", "26,000"]},
                    {"values": ["เสียហាប/REPDP01", "58,000"]}
                  ]
                }
                """);

        assertNotNull(extracted);
        assertEquals("សាខា សៀមរាប/REPDP01", extracted.getRows().get(0).get(0));
        assertEquals("សៀមរាប/REPDP01", extracted.getRows().get(1).get(0));
    }
}
