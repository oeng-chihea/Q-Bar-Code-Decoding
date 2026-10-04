package com.excel.reconciler.service;

import com.excel.reconciler.model.BarcodeResult;
import com.excel.reconciler.model.UnmatchedWaybill;
import com.excel.reconciler.model.WaybillStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReconciliationServiceTest {

    private final WaybillTrackingService tracking = mock(WaybillTrackingService.class);
    private final UnmatchedWaybillSheetWriter sheetWriter = mock(UnmatchedWaybillSheetWriter.class);

    private ReconciliationService newService(BarcodeDecoderService barcodeDecoder,
                                             ExcelHighlightService highlighter,
                                             ExcelImageExtractorService extractor) throws IOException {
        when(tracking.track(any(), any(), any()))
                .thenReturn(new WaybillTrackingService.TrackingResult(false, 6, List.of()));
        when(sheetWriter.append(any(), any(), anyInt())).thenAnswer(call -> call.getArgument(0));
        return new ReconciliationService(barcodeDecoder, highlighter, extractor, tracking, sheetWriter);
    }

    @Test
    void reconcilesAnExcelTableImageThroughTheExtractor() throws Exception {
        BarcodeDecoderService barcodeDecoder = mock(BarcodeDecoderService.class);
        ExcelHighlightService highlighter = mock(ExcelHighlightService.class);
        ExcelImageExtractorService extractor = mock(ExcelImageExtractorService.class);

        MockMultipartFile tableImage = new MockMultipartFile(
                "excelFile", "inventory.png", "image/png", new byte[]{9, 8, 7});

        when(extractor.processExcelImage(tableImage)).thenReturn(
                new ExcelImageExtractorService.ExtractedExcelData(
                        true, false, null, "Scanned Inventory", List.of("Barcode"),
                        List.of(List.of("SKU-1")), new byte[]{1, 2, 3}));
        when(barcodeDecoder.decodeBatch(any())).thenReturn(Collections.emptyList());
        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), eq("Barcode"), eq(false)))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, 1, 0, "Barcode", "Scanned Inventory",
                        List.of("Barcode"), Collections.emptySet(), Collections.emptyList()));

        ReconciliationService service = newService(barcodeDecoder, highlighter, extractor);
        var response = service.reconcile(tableImage, List.of(), "Barcode", false, "rec-1");

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
        ReconciliationService service = newService(barcodeDecoder, highlighter, extractor);

        MockMultipartFile barcodeImage = new MockMultipartFile(
                "excelFile", "barcode.png", "image/png", new byte[]{1, 2, 3});
        when(extractor.processExcelImage(barcodeImage)).thenThrow(
                new IllegalArgumentException("Barcode images are not supported in the Excel section."));

        var error = assertThrows(IllegalArgumentException.class, () ->
                service.reconcile(barcodeImage, List.of(), "Barcode", false, "rec-1"));

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

        ReconciliationService service = newService(barcodeDecoder, highlighter, extractor);
        var response = service.reconcile(tableImage, List.of(scanImage), "Waybill Number", false, "rec-1");

        assertEquals(1, response.getMatchedRowsCount());
        assertEquals(0, response.getUnmatchedImagesCount());
        assertTrue(response.getUnmatchedCodes().isEmpty());
        assertEquals("J01396943696", response.getScanResults().get(0).getDecodedValue());
        assertTrue(response.getScanResults().get(0).isMatched());
    }

    @Test
    void tracksUnmatchedAndMatchedCodesAndExposesTheTrackingResult() throws Exception {
        BarcodeDecoderService barcodeDecoder = mock(BarcodeDecoderService.class);
        ExcelHighlightService highlighter = mock(ExcelHighlightService.class);
        ExcelImageExtractorService extractor = mock(ExcelImageExtractorService.class);
        MockMultipartFile tableImage = new MockMultipartFile("excelFile", "inventory.png", "image/png", new byte[]{1});

        when(extractor.processExcelImage(tableImage)).thenReturn(
                new ExcelImageExtractorService.ExtractedExcelData(
                        true, false, null, "Sheet", List.of("Waybill Number"),
                        List.of(List.of("J0001")), new byte[]{1, 2, 3}));
        when(barcodeDecoder.decodeBatch(any())).thenReturn(List.of(
                new BarcodeResult("1.jpg", "J0001", List.of("J0001"), "ZXING", true, "CODE_128", null),
                new BarcodeResult("2.jpg", "J9999", List.of("J9999"), "ZXING", true, "CODE_128", null),
                new BarcodeResult("3.jpg", null, Collections.emptyList(), "FAILED", false, null, "no barcode")));
        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), any(), eq(false)))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, 1, 1, "Waybill Number", "Sheet",
                        List.of("Waybill Number"), Set.of("J0001"), Collections.emptyList()));

        ReconciliationService service = newService(barcodeDecoder, highlighter, extractor);
        UnmatchedWaybill overdue = new UnmatchedWaybill("J9999", LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 26), 14, 0, 8, WaybillStatus.OVERDUE, true);
        when(tracking.track(any(), any(), any()))
                .thenReturn(new WaybillTrackingService.TrackingResult(true, 6, List.of(overdue)));

        var response = service.reconcile(tableImage, List.of(), "Waybill Number", false, "rec-1");

        // Only waybills that were actually read are tracked; the image with no barcode is not
        verify(tracking).track(eq(Set.of("J9999")), eq(Set.of("J0001")), eq("rec-1"));
        assertTrue(response.isTrackingAvailable());
        assertEquals(6, response.getOverdueAfterDays());
        assertEquals(1, response.getOverdueCount());
        assertEquals(List.of(overdue), response.getUnmatchedWaybills());
        verify(sheetWriter).append(eq(new byte[]{4, 5}), eq(List.of(overdue)), eq(6));
    }

    @Test
    void keepsTheHighlightedWorkbookWhenTheUnmatchedSheetCannotBeWritten() throws Exception {
        BarcodeDecoderService barcodeDecoder = mock(BarcodeDecoderService.class);
        ExcelHighlightService highlighter = mock(ExcelHighlightService.class);
        ExcelImageExtractorService extractor = mock(ExcelImageExtractorService.class);
        MockMultipartFile tableImage = new MockMultipartFile("excelFile", "inventory.png", "image/png", new byte[]{1});

        when(extractor.processExcelImage(tableImage)).thenReturn(
                new ExcelImageExtractorService.ExtractedExcelData(
                        true, false, null, "Sheet", List.of("Waybill Number"),
                        List.of(List.of("J0001")), new byte[]{1, 2, 3}));
        when(barcodeDecoder.decodeBatch(any())).thenReturn(Collections.emptyList());
        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), any(), eq(false)))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, 1, 0, "Waybill Number", "Sheet",
                        List.of("Waybill Number"), Collections.emptySet(), Collections.emptyList()));

        ReconciliationService service = newService(barcodeDecoder, highlighter, extractor);
        when(sheetWriter.append(any(), any(), anyInt())).thenThrow(new IOException("disk full"));

        var response = service.reconcile(tableImage, List.of(), "Waybill Number", false, "rec-1");

        assertArrayEquals(new byte[]{4, 5}, Base64.getDecoder().decode(response.getHighlightedExcelBase64()));
        assertFalse(response.isTrackingAvailable());
    }
}
