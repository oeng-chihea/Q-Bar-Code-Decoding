package com.excel.reconciler.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReconciliationServiceTest {

    @Test
    void reconcilesAnExcelTableImageThroughTheExtractor() throws Exception {
        BarcodeDecoderService barcodeDecoder = mock(BarcodeDecoderService.class);
        ExcelHighlightService highlighter = mock(ExcelHighlightService.class);
        ExcelImageExtractorService extractor = mock(ExcelImageExtractorService.class);

        MockMultipartFile tableImage = new MockMultipartFile(
                "excelFile", "inventory.png", "image/png", new byte[]{9, 8, 7});

        when(extractor.isImage(tableImage)).thenReturn(true);
        when(extractor.processExcelImage(tableImage)).thenReturn(
                new ExcelImageExtractorService.ExtractedExcelData(
                        true, false, null, "Scanned Inventory", List.of("Barcode"),
                        List.of(List.of("SKU-1")), new byte[]{1, 2, 3}));
        when(barcodeDecoder.decodeBatch(any())).thenReturn(Collections.emptyList());
        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), eq("Barcode"), eq(false)))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, 1, 0, "Barcode", "Scanned Inventory",
                        List.of("Barcode"), Collections.emptySet(), Collections.emptyList()));

        ReconciliationService service = new ReconciliationService(barcodeDecoder, highlighter, extractor);
        var response = service.reconcile(tableImage, List.of(), "Barcode", false);

        assertEquals("EXCEL_TABLE_IMAGE", response.getExcelSourceType());
        assertEquals("inventory_highlighted.xlsx", response.getDownloadFileName());
        verify(extractor).processExcelImage(tableImage);
        verify(highlighter).highlightMatches(any(ByteArrayInputStream.class), anySet(), eq("Barcode"), eq(false));
    }

    @Test
    void rejectsBarcodeImageReturnedByTheTableExtractor() throws Exception {
        BarcodeDecoderService barcodeDecoder = mock(BarcodeDecoderService.class);
        ExcelHighlightService highlighter = mock(ExcelHighlightService.class);
        ExcelImageExtractorService extractor = mock(ExcelImageExtractorService.class);
        ReconciliationService service = new ReconciliationService(barcodeDecoder, highlighter, extractor);

        MockMultipartFile barcodeImage = new MockMultipartFile(
                "excelFile", "barcode.png", "image/png", new byte[]{1, 2, 3});
        when(extractor.isImage(barcodeImage)).thenReturn(true);
        when(extractor.processExcelImage(barcodeImage)).thenThrow(
                new IllegalArgumentException("Barcode images are not supported in the Excel section."));

        var error = assertThrows(IllegalArgumentException.class, () ->
                service.reconcile(barcodeImage, List.of(), "Barcode", false));

        assertEquals("Barcode images are not supported in the Excel section.", error.getMessage());
        verifyNoInteractions(barcodeDecoder, highlighter);
    }

    @Test
    void imageWithMultipleCandidatesPromotesMatchedBarcodeAndDoesNotProducePhantomUnmatchedCode() throws Exception {
        BarcodeDecoderService barcodeDecoder = mock(BarcodeDecoderService.class);
        ExcelHighlightService highlighter = mock(ExcelHighlightService.class);
        ExcelImageExtractorService extractor = mock(ExcelImageExtractorService.class);

        MockMultipartFile tableImage = new MockMultipartFile(
                "excelFile", "inventory.png", "image/png", new byte[]{1});
        MockMultipartFile scanImage = new MockMultipartFile(
                "images", "2.jpg", "image/jpeg", new byte[]{2});

        when(extractor.isImage(tableImage)).thenReturn(true);
        when(extractor.processExcelImage(tableImage)).thenReturn(
                new ExcelImageExtractorService.ExtractedExcelData(
                        true, false, null, "Sheet", List.of("Waybill Number"),
                        List.of(List.of("J01396943696")), new byte[]{1, 2, 3}));

        var barcodeResult = new com.excel.reconciler.model.BarcodeResult(
                "2.jpg", "00505718", List.of("J01396943696", "00505718"), "ZXING", true, "CODE_128", null);
        when(barcodeDecoder.decodeBatch(any())).thenReturn(List.of(barcodeResult));

        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), any(), eq(false)))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, 1, 1, "Waybill Number", "Sheet",
                        List.of("Waybill Number"), java.util.Set.of("J01396943696"), Collections.emptyList()));

        ReconciliationService service = new ReconciliationService(barcodeDecoder, highlighter, extractor);
        var response = service.reconcile(tableImage, List.of(scanImage), "Waybill Number", false);

        assertEquals(1, response.getMatchedRowsCount());
        assertEquals(0, response.getUnmatchedImagesCount());
        assertTrue(response.getUnmatchedCodes().isEmpty());
        assertEquals("J01396943696", response.getScanResults().get(0).getDecodedValue());
        assertTrue(response.getScanResults().get(0).isMatched());
    }
}
