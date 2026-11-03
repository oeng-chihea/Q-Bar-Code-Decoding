package com.excel.reconciler.service;

import com.excel.reconciler.model.BarcodeResult;
import com.excel.reconciler.model.UnmatchedWaybill;
import com.excel.reconciler.model.WaybillStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReconciliationServiceTest {

    private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");

    // Today is Nov 3, 2026 in Cambodia
    private final WaybillTrackingService tracking = new WaybillTrackingService(6,
            Clock.fixed(LocalDate.of(2026, 11, 3).atTime(12, 0).atZone(PHNOM_PENH).toInstant(), PHNOM_PENH));
    private final UnmatchedWaybillSheetWriter sheetWriter = mock(UnmatchedWaybillSheetWriter.class);

    private ReconciliationService newService(BarcodeDecoderService barcodeDecoder,
                                             ExcelHighlightService highlighter,
                                             ExcelImageExtractorService extractor) throws IOException {
        when(sheetWriter.append(any(), any(), anyInt())).thenAnswer(call -> call.getArgument(0));
        return new ReconciliationService(barcodeDecoder, highlighter, extractor, tracking, sheetWriter,
                Optional.empty());
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
        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), eq("Barcode"), eq(false), anyMap()))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, 1, 0, "Barcode", "Scanned Inventory",
                        List.of("Barcode"), Collections.emptySet(), Collections.emptyList()));

        ReconciliationService service = newService(barcodeDecoder, highlighter, extractor);
        var response = service.reconcile(tableImage, List.of(), "Barcode", false);

        assertEquals("EXCEL_TABLE_IMAGE", response.getExcelSourceType());
        assertEquals("inventory_highlighted.xlsx", response.getDownloadFileName());
        verify(extractor).processExcelImage(tableImage);
        verify(highlighter).highlightMatches(any(ByteArrayInputStream.class), anySet(), eq("Barcode"), eq(false), anyMap());
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
                service.reconcile(barcodeImage, List.of(), "Barcode", false));

        assertEquals("Barcode images are not supported in the Excel section.", error.getMessage());
        verifyNoInteractions(highlighter);
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

        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), any(), eq(false), anyMap()))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, 1, 1, "Waybill Number", "Sheet",
                        List.of("Waybill Number"), java.util.Set.of("J01396943696"), Collections.emptyList()));

        ReconciliationService service = newService(barcodeDecoder, highlighter, extractor);
        var response = service.reconcile(tableImage, List.of(scanImage), "Waybill Number", false);

        assertEquals(1, response.getMatchedRowsCount());
        assertEquals(0, response.getUnmatchedImagesCount());
        assertTrue(response.getUnmatchedCodes().isEmpty());
        assertEquals("J01396943696", response.getScanResults().get(0).getDecodedValue());
        assertTrue(response.getScanResults().get(0).isMatched());
    }

    @Test
    void countsUnmatchedSerialNumbersFromTheirReceiptsCreatedDate() throws Exception {
        BarcodeDecoderService barcodeDecoder = mock(BarcodeDecoderService.class);
        ExcelHighlightService highlighter = mock(ExcelHighlightService.class);
        ExcelImageExtractorService extractor = mock(ExcelImageExtractorService.class);
        MockMultipartFile tableImage = new MockMultipartFile("excelFile", "inventory.png", "image/png", new byte[]{1});

        when(extractor.processExcelImage(tableImage)).thenReturn(
                new ExcelImageExtractorService.ExtractedExcelData(
                        true, false, null, "Sheet", List.of("Waybill Number"),
                        List.of(List.of("J0001")), new byte[]{1, 2, 3}));
        BarcodeResult unmatchedScan = new BarcodeResult("2.jpg", "J9999", List.of("J9999"), "ZXING", true, "CODE_128", null);
        unmatchedScan.setCreateDate("2026-10-15");
        when(barcodeDecoder.decodeBatch(any())).thenReturn(List.of(
                new BarcodeResult("1.jpg", "J0001", List.of("J0001"), "ZXING", true, "CODE_128", null),
                unmatchedScan,
                new BarcodeResult("3.jpg", null, Collections.emptyList(), "FAILED", false, null, "no barcode")));
        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), any(), eq(false), anyMap()))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, 1, 1, "Waybill Number", "Sheet",
                        List.of("Waybill Number"), Set.of("J0001"), Collections.emptyList()));

        ReconciliationService service = newService(barcodeDecoder, highlighter, extractor);

        var response = service.reconcile(tableImage, List.of(), "Waybill Number", false);

        // Oct 15 to Nov 3 is 19 days: 13 over the 6-day limit. Matched and unread images are not listed.
        UnmatchedWaybill overdue = new UnmatchedWaybill("J9999", LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 21), 19, 0, 13, WaybillStatus.OVERDUE, null);
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
        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), any(), eq(false), anyMap()))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, 1, 0, "Waybill Number", "Sheet",
                        List.of("Waybill Number"), Collections.emptySet(), Collections.emptyList()));

        ReconciliationService service = newService(barcodeDecoder, highlighter, extractor);
        when(sheetWriter.append(any(), any(), anyInt())).thenThrow(new IOException("disk full"));

        var response = service.reconcile(tableImage, List.of(), "Waybill Number", false);

        assertArrayEquals(new byte[]{4, 5}, Base64.getDecoder().decode(response.getHighlightedExcelBase64()));
    }

    private static BarcodeResult misreadReceipt(String barcode, String printed) {
        BarcodeResult result = new BarcodeResult("photo_12.jpg", barcode, List.of(barcode), "ZXING", true, "CODE_128", null);
        result.setCreateDateText("Oct 6, 2026, 10:07 AM");
        result.setCreateDateProblem("WAYBILL_MISMATCH");
        result.setPrintedWaybill(printed);
        result.setPrintedWaybillCreateDate("2026-10-06");
        result.setPrintedWaybillCustomerName("Nhim Sarey");
        return result;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Set<String>> reconcileWithTable(BarcodeResult scan, List<List<String>> rows) throws Exception {
        BarcodeDecoderService barcodeDecoder = mock(BarcodeDecoderService.class);
        ExcelHighlightService highlighter = mock(ExcelHighlightService.class);
        ExcelImageExtractorService extractor = mock(ExcelImageExtractorService.class);
        MockMultipartFile tableImage = new MockMultipartFile("excelFile", "inventory.png", "image/png", new byte[]{1});

        when(extractor.processExcelImage(tableImage)).thenReturn(
                new ExcelImageExtractorService.ExtractedExcelData(
                        true, false, null, "Sheet", List.of("Waybill Number"), rows, new byte[]{1, 2, 3}));
        when(barcodeDecoder.decodeBatch(any())).thenReturn(List.of(scan));
        when(highlighter.findMatchingDecodedCode(any(), anyMap())).thenCallRealMethod();
        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), any(), eq(false), anyMap()))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, rows.size(), 1, "Waybill Number", "Sheet",
                        List.of("Waybill Number"), Set.of("J01421594677"), Collections.emptyList()));

        newService(barcodeDecoder, highlighter, extractor)
                .reconcile(tableImage, List.of(tableImage), "Waybill Number", false);

        ArgumentCaptor<Set<String>> codes = ArgumentCaptor.forClass(Set.class);
        verify(highlighter).highlightMatches(any(ByteArrayInputStream.class), codes.capture(), any(), eq(false), anyMap());
        return codes;
    }

    @Test
    void aReceiptWhoseBarcodeIsInNoRowUsesItsPrintedWaybillAndDate() throws Exception {
        BarcodeResult scan = misreadReceipt("J00521590777", "J01421594677");

        ArgumentCaptor<Set<String>> codes = reconcileWithTable(scan, List.of(List.of("4", "J01419454153"), List.of("5", "J01421594677")));

        assertEquals(Set.of("J01421594677"), codes.getValue());
        assertEquals("J01421594677", scan.getDecodedValue());
        assertEquals("2026-10-06", scan.getCreateDate());
        assertEquals(null, scan.getCreateDateProblem());
        assertEquals("Nhim Sarey", scan.getCustomerName());
        assertTrue(scan.isMatched());
    }

    @Test
    void aReceiptWhoseBarcodeIsInTheTableKeepsItsBarcodeAndTheMismatchWarning() throws Exception {
        BarcodeResult scan = misreadReceipt("J01419454153", "J01421594677");

        ArgumentCaptor<Set<String>> codes = reconcileWithTable(scan, List.of(List.of("4", "J01419454153"), List.of("5", "J01421594677")));

        assertEquals(Set.of("J01419454153"), codes.getValue());
        assertEquals(null, scan.getCreateDate());
        assertEquals("WAYBILL_MISMATCH", scan.getCreateDateProblem());
    }

    @Test
    void aPrintedWaybillThatIsInNoRowIsNotUsed() throws Exception {
        BarcodeResult scan = misreadReceipt("J00521590777", "J01421594677");

        ArgumentCaptor<Set<String>> codes = reconcileWithTable(scan, List.of(List.of("4", "J01419454153")));

        assertEquals(Set.of("J00521590777"), codes.getValue());
        assertEquals("WAYBILL_MISMATCH", scan.getCreateDateProblem());
    }

    @Test
    void unmatchedSerialNumbersTakeTheirDateAndNameFromThisUploadOrEarlierReceipts() throws Exception {
        BarcodeDecoderService barcodeDecoder = mock(BarcodeDecoderService.class);
        ExcelHighlightService highlighter = mock(ExcelHighlightService.class);
        ExcelImageExtractorService extractor = mock(ExcelImageExtractorService.class);
        ReceiptDetailStore store = mock(ReceiptDetailStore.class);
        MockMultipartFile tableImage = new MockMultipartFile("excelFile", "inventory.png", "image/png", new byte[]{1});

        when(extractor.processExcelImage(tableImage)).thenReturn(
                new ExcelImageExtractorService.ExtractedExcelData(
                        true, false, null, "Sheet", List.of("Waybill Number"),
                        List.of(List.of("J0001")), new byte[]{1, 2, 3}));
        BarcodeResult unmatchedScan = new BarcodeResult("2.jpg", "J9999", List.of("J9999"), "ZXING", true, "CODE_128", null);
        unmatchedScan.setCreateDate("2026-10-28");
        unmatchedScan.setCustomerName("Nhim Sarey");
        when(barcodeDecoder.decodeBatch(any())).thenReturn(List.of(unmatchedScan,
                new BarcodeResult("3.jpg", "J8888", List.of("J8888"), "ZXING", true, "CODE_128", null),
                new BarcodeResult("4.jpg", "J7777", List.of("J7777"), "ZXING", true, "CODE_128", null)));
        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), any(), eq(false), anyMap()))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, 1, 0, "Waybill Number", "Sheet",
                        List.of("Waybill Number"), Set.of(), Collections.emptyList()));
        when(sheetWriter.append(any(), any(), anyInt())).thenAnswer(call -> call.getArgument(0));
        when(store.findReceipts(any())).thenReturn(Map.of(
                "J8888", new ReceiptDetailStore.SavedReceipt(LocalDate.of(2026, 10, 19), "Oct 19, 2026", "Sok Dara")));

        var response = new ReconciliationService(barcodeDecoder, highlighter, extractor, tracking, sheetWriter,
                Optional.of(store)).reconcile(tableImage, List.of(tableImage), "Waybill Number", false);

        // Oldest CREATED date first; the one without any date comes last
        List<UnmatchedWaybill> expected = List.of(
                new UnmatchedWaybill("J8888", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25),
                        15, 0, 9, WaybillStatus.OVERDUE, "Sok Dara"),
                new UnmatchedWaybill("J9999", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3),
                        6, 0, 0, WaybillStatus.DUE_TODAY, "Nhim Sarey"),
                new UnmatchedWaybill("J7777", null, null, 0, 0, 0, WaybillStatus.NO_DATE, null));
        assertEquals(expected, response.getUnmatchedWaybills());
        verify(store).findReceipts(List.of("J8888", "J7777"));
        verify(sheetWriter).append(any(), eq(expected), eq(6));
    }

    @Test
    void aRepeatPhotoOfAMatchedRowCountsAsALeftoverImageNotAMatch() throws Exception {
        BarcodeDecoderService barcodeDecoder = mock(BarcodeDecoderService.class);
        ExcelHighlightService highlighter = mock(ExcelHighlightService.class);
        ExcelImageExtractorService extractor = mock(ExcelImageExtractorService.class);
        MockMultipartFile tableImage = new MockMultipartFile("excelFile", "inventory.png", "image/png", new byte[]{1});
        MockMultipartFile photo = new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{2});

        when(extractor.processExcelImage(tableImage)).thenReturn(
                new ExcelImageExtractorService.ExtractedExcelData(
                        true, false, null, "Sheet", List.of("Waybill Number"),
                        List.of(List.of("J0001"), List.of("J0002")), new byte[]{1, 2, 3}));
        when(barcodeDecoder.decodeBatch(any())).thenReturn(List.of(
                new com.excel.reconciler.model.BarcodeResult("1.jpg", "J0001", List.of("J0001"), "ZXING", true, "CODE_128", null),
                new com.excel.reconciler.model.BarcodeResult("2.jpg", "J0001", List.of("J0001"), "ZXING", true, "CODE_128", null),
                new com.excel.reconciler.model.BarcodeResult("3.jpg", "J0002", List.of("J0002"), "ZXING", true, "CODE_128", null),
                new com.excel.reconciler.model.BarcodeResult("4.jpg", "J9999", List.of("J9999"), "ZXING", true, "CODE_128", null)));
        when(highlighter.highlightMatches(any(ByteArrayInputStream.class), anySet(), any(), eq(false), anyMap()))
                .thenReturn(new ExcelHighlightService.ExcelProcessingResult(
                        new byte[]{4, 5}, 2, 2, "Waybill Number", "Sheet",
                        List.of("Waybill Number"), java.util.Set.of("J0001", "J0002"), Collections.emptyList()));

        ReconciliationService service = newService(barcodeDecoder, highlighter, extractor);
        var response = service.reconcile(tableImage, List.of(photo, photo, photo, photo), "Waybill Number", false);

        var scans = response.getScanResults();
        assertTrue(scans.get(0).isMatched());
        assertFalse(scans.get(0).isDuplicate());
        assertFalse(scans.get(1).isMatched());
        assertTrue(scans.get(1).isDuplicate());
        assertTrue(scans.get(2).isMatched());
        assertFalse(scans.get(3).isMatched());
        assertFalse(scans.get(3).isDuplicate());
        // 2 rows matched + 2 leftover images (the repeat and the unknown waybill) = the 4 uploaded photos
        assertEquals(2, response.getMatchedRowsCount());
        assertEquals(2, response.getUnmatchedImagesCount());
        // The repeat's waybill is matched, so only the unknown one is tracked as unmatched
        assertEquals(java.util.Set.of("J9999"), response.getUnmatchedCodes());
    }
}
