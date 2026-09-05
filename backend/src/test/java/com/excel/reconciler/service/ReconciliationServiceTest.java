package com.excel.reconciler.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
