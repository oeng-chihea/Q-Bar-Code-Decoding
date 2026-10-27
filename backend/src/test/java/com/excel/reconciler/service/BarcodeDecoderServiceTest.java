package com.excel.reconciler.service;

import com.excel.reconciler.model.BarcodeResult;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class BarcodeDecoderServiceTest {

    @Test
    void skipsGeminiWhenZxingSuccessfullyDecodesBarcode() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = new BarcodeDecoderService(zxing, gemini, Runnable::run);
        MockMultipartFile image = new MockMultipartFile("image", "barcode.png", "image/png", new byte[]{1, 2, 3});

        when(zxing.decode(any(byte[].class))).thenReturn(Collections.singletonList(
                new Result("01400310465", null, null, BarcodeFormat.CODE_128)));

        BarcodeResult result = service.decodeBatch(List.of(image)).get(0);

        assertTrue(result.isSuccess());
        assertEquals("01400310465", result.getDecodedValue());
        assertEquals("ZXING", result.getDecoderType());
        verify(gemini, never()).extractBarcodesParallel(any());
    }

    @Test
    void usesGeminiOnlyWhenZxingFindsNoUsableBarcode() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = new BarcodeDecoderService(zxing, gemini, Runnable::run);
        MockMultipartFile image = new MockMultipartFile("image", "barcode.png", "image/png", new byte[]{1, 2, 3});

        when(zxing.decode(any(byte[].class))).thenReturn(Collections.emptyList());
        when(gemini.extractBarcodesParallel(any()))
                .thenReturn(List.of(List.of("01400310465")));

        BarcodeResult result = service.decodeBatch(List.of(image)).get(0);

        assertTrue(result.isSuccess());
        assertEquals("01400310465", result.getDecodedValue());
        assertEquals("GEMINI_AI", result.getDecoderType());
        verify(gemini).extractBarcodesParallel(any());
    }

    @Test
    void batchesGeminiFallbacksAfterLocalDecodeFailures() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = new BarcodeDecoderService(zxing, gemini, Runnable::run);
        List<MultipartFile> images = List.of(
                new MockMultipartFile("images", "first.png", "image/png", new byte[]{1}),
                new MockMultipartFile("images", "second.png", "image/png", new byte[]{2})
        );

        when(zxing.decode(any(byte[].class))).thenReturn(Collections.emptyList());
        when(gemini.extractBarcodesParallel(any()))
                .thenReturn(List.of(List.of("01400310465"), List.of("01400310466")));

        List<BarcodeResult> results = service.decodeBatch(images);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(BarcodeResult::isSuccess));
        assertEquals("01400310465", results.get(0).getDecodedValue());
        assertEquals("01400310466", results.get(1).getDecodedValue());
        verify(gemini).extractBarcodesParallel(any());
    }

    @Test
    void chunksLargeFallbackSetsIntoSmallAiBatches() {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = new BarcodeDecoderService(zxing, gemini, Runnable::run);
        List<MultipartFile> images = List.of(
                new MockMultipartFile("images", "one.png", "image/png", new byte[]{1}),
                new MockMultipartFile("images", "two.png", "image/png", new byte[]{2}),
                new MockMultipartFile("images", "three.png", "image/png", new byte[]{3}),
                new MockMultipartFile("images", "four.png", "image/png", new byte[]{4}),
                new MockMultipartFile("images", "five.png", "image/png", new byte[]{5})
        );

        when(zxing.decode(any(byte[].class))).thenReturn(Collections.emptyList());
        when(gemini.extractBarcodesParallel(any())).thenAnswer(invocation -> {
            List<?> batch = invocation.getArgument(0);
            return batch.stream()
                    .map(ignored -> List.of("01400310465"))
                    .toList();
        });

        service.decodeBatch(images);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GeminiVisionService.BarcodeImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(gemini, times(2)).extractBarcodesParallel(captor.capture());
        assertEquals(List.of(4, 1), captor.getAllValues().stream().map(List::size).toList());
    }

    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-10-25T05:00:00Z"), ZoneId.of("Asia/Phnom_Penh"));

    private BarcodeDecoderService serviceWithToday(ZXingDecoderService zxing, GeminiVisionService gemini) {
        return new BarcodeDecoderService(zxing, gemini, Runnable::run, TODAY);
    }

    private static GeminiVisionService.ReceiptDateRead read(String text) {
        return GeminiVisionService.ReceiptDateRead.found(text);
    }

    private static void decodeWaybillFromFirstByte(ZXingDecoderService zxing) throws Exception {
        when(zxing.decode(any(byte[].class))).thenAnswer(call -> {
            byte[] bytes = call.getArgument(0);
            return List.of(new Result(waybill(bytes[0]), null, null, BarcodeFormat.CODE_128));
        });
    }

    private static String waybill(int firstByte) {
        return "J0140031046" + firstByte;
    }

    private static void answerDates(GeminiVisionService gemini, Function<Integer, GeminiVisionService.ReceiptDateRead> byFirstByte) {
        when(gemini.extractReceiptCreatedBatch(any())).thenAnswer(call -> {
            List<GeminiVisionService.BarcodeImage> images = call.getArgument(0);
            return images.stream().map(image -> byFirstByte.apply((int) image.imageBytes()[0])).toList();
        });
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> requestSizes(GeminiVisionService gemini, int requests) {
        ArgumentCaptor<List<GeminiVisionService.BarcodeImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(gemini, times(requests)).extractReceiptCreatedBatch(captor.capture());
        return captor.getAllValues().stream().map(List::size).toList();
    }

    private static List<MultipartFile> photos(int... firstBytes) {
        return java.util.Arrays.stream(firstBytes)
                .mapToObj(b -> (MultipartFile) new MockMultipartFile("images", "photo-" + b + ".png", "image/png", new byte[]{(byte) b}))
                .toList();
    }

    @Test
    void readsAndValidatesTheCreatedDateForEveryDecodedReceiptInOneRequest() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = serviceWithToday(zxing, gemini);

        decodeWaybillFromFirstByte(zxing);
        answerDates(gemini, b -> Map.of(
                1, read("Oct 18, 2026, 02:29 PM"), 2, read(""), 3, read("Dec 01, 2026, 09:00 AM")).get(b));

        List<BarcodeResult> results = service.decodeBatch(photos(1, 2, 3));

        assertEquals(List.of(3), requestSizes(gemini, 1));
        assertEquals("2026-10-18", results.get(0).getCreateDate());
        assertEquals(null, results.get(0).getCreateDateProblem());
        assertEquals(null, results.get(1).getCreateDate());
        assertEquals("NOT_FOUND", results.get(1).getCreateDateProblem());
        assertEquals("FUTURE_DATE", results.get(2).getCreateDateProblem());
        assertEquals("Dec 01, 2026, 09:00 AM", results.get(2).getCreateDateText());
    }

    @Test
    void sendsReceiptsInRequestsOfTheConfiguredBatchSize() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = new BarcodeDecoderService(
                zxing, gemini, Runnable::run, TODAY, Optional.empty(), Duration.ofSeconds(60), 2);

        decodeWaybillFromFirstByte(zxing);
        answerDates(gemini, b -> read("Oct " + b + ", 2026, 09:00 AM"));

        List<BarcodeResult> results = service.decodeBatch(photos(1, 2, 3, 4, 5));

        assertEquals(List.of(2, 2, 1), requestSizes(gemini, 3));
        for (int i = 0; i < 5; i++) {
            assertEquals("2026-10-0" + (i + 1), results.get(i).getCreateDate());
        }
    }

    @Test
    void photosOfTheSameWaybillAreReadOnceAndShareTheResult() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = serviceWithToday(zxing, gemini);
        List<MultipartFile> images = List.of(
                new MockMultipartFile("images", "a.png", "image/png", new byte[]{1, 1}),
                new MockMultipartFile("images", "b.png", "image/png", new byte[]{2, 2}),
                new MockMultipartFile("images", "a-again.png", "image/png", new byte[]{1, 9})
        );

        decodeWaybillFromFirstByte(zxing);
        answerDates(gemini, b -> b == 1 ? read("Oct 5, 2026, 04:14 PM") : read("Oct 6, 2026, 10:07 AM"));

        List<BarcodeResult> results = service.decodeBatch(images);

        assertEquals(List.of(2), requestSizes(gemini, 1));
        assertEquals("2026-10-05", results.get(0).getCreateDate());
        assertEquals("2026-10-06", results.get(1).getCreateDate());
        assertEquals("2026-10-05", results.get(2).getCreateDate());
    }

    @Test
    void aFailedReadIsReportedOnceAndNotRetried() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = serviceWithToday(zxing, gemini);

        decodeWaybillFromFirstByte(zxing);
        answerDates(gemini, b -> b == 1 ? GeminiVisionService.ReceiptDateRead.failure() : read("Oct 8, 2026, 01:00 PM"));

        List<BarcodeResult> results = service.decodeBatch(photos(1, 2));

        requestSizes(gemini, 1);
        assertEquals("READ_FAILED", results.get(0).getCreateDateProblem());
        assertEquals(null, results.get(0).getCreateDate());
        assertEquals("2026-10-08", results.get(1).getCreateDate());
    }

    @Test
    void aShortOrMissingAnswerCountsAsFailedForTheReceiptsLeftOut() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = serviceWithToday(zxing, gemini);

        decodeWaybillFromFirstByte(zxing);
        when(gemini.extractReceiptCreatedBatch(any())).thenReturn(List.of(read("Oct 8, 2026, 01:00 PM")));

        List<BarcodeResult> results = service.decodeBatch(photos(1, 2));

        assertEquals("2026-10-08", results.get(0).getCreateDate());
        assertEquals("READ_FAILED", results.get(1).getCreateDateProblem());
    }

    @Test
    void readsStillRunningWhenTheTimeBudgetRunsOutAreReportedAsNotRead() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            BarcodeDecoderService service = new BarcodeDecoderService(
                    zxing, gemini, pool, TODAY, Optional.empty(), Duration.ofMillis(300), 1);

            decodeWaybillFromFirstByte(zxing);
            answerDates(gemini, b -> {
                if (b == 2) {
                    sleep(5_000);
                }
                return read("Oct 8, 2026, 01:00 PM");
            });

            long started = System.nanoTime();
            List<BarcodeResult> results = service.decodeBatch(photos(1, 2));
            long tookMs = (System.nanoTime() - started) / 1_000_000;

            assertTrue(tookMs < 2_000, "took " + tookMs + " ms");
            assertEquals("2026-10-08", results.get(0).getCreateDate());
            assertEquals("READ_FAILED", results.get(1).getCreateDateProblem());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void requestsStillWaitingForAWorkerWhenTheBudgetRunsOutAreNeverSent() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ExecutorService singleWorker = Executors.newSingleThreadExecutor();
        try {
            BarcodeDecoderService service = new BarcodeDecoderService(
                    zxing, gemini, singleWorker, TODAY, Optional.empty(), Duration.ofMillis(200), 1);

            decodeWaybillFromFirstByte(zxing);
            answerDates(gemini, b -> {
                sleep(800);
                return read("Oct 8, 2026, 01:00 PM");
            });

            List<BarcodeResult> results = service.decodeBatch(photos(1, 2));
            Thread.sleep(1_500);

            requestSizes(gemini, 1);
            assertEquals("READ_FAILED", results.get(0).getCreateDateProblem());
            assertEquals("READ_FAILED", results.get(1).getCreateDateProblem());
        } finally {
            singleWorker.shutdownNow();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void savedDatesAreReusedAndOnlyNewlyReadDatesAreSaved() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ReceiptDateStore store = mock(ReceiptDateStore.class);
        BarcodeDecoderService service = new BarcodeDecoderService(
                zxing, gemini, Runnable::run, TODAY, Optional.of(store), Duration.ofSeconds(60));

        decodeWaybillFromFirstByte(zxing);
        when(store.findDates(any())).thenReturn(Map.of(
                waybill(1), new ReceiptDateStore.SavedDate(LocalDate.of(2026, 10, 3), "Oct 3, 2026, 09:15 AM")));
        answerDates(gemini, b -> read("Oct 9, 2026, 11:00 AM"));

        List<BarcodeResult> results = service.decodeBatch(photos(1, 2));

        assertEquals(List.of(1), requestSizes(gemini, 1));
        assertEquals("2026-10-03", results.get(0).getCreateDate());
        assertEquals("Oct 3, 2026, 09:15 AM", results.get(0).getCreateDateText());
        assertEquals("2026-10-09", results.get(1).getCreateDate());
        verify(store).saveDates(Map.of(
                waybill(2), new ReceiptDateStore.SavedDate(LocalDate.of(2026, 10, 9), "Oct 9, 2026, 11:00 AM")));
    }

    @Test
    void aBrokenDatabaseOnlyMeansDatesAreReadAgain() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ReceiptDateStore store = mock(ReceiptDateStore.class);
        BarcodeDecoderService service = new BarcodeDecoderService(
                zxing, gemini, Runnable::run, TODAY, Optional.of(store), Duration.ofSeconds(60));

        decodeWaybillFromFirstByte(zxing);
        when(store.findDates(any())).thenThrow(new IllegalStateException("connection refused"));
        doThrow(new IllegalStateException("connection refused")).when(store).saveDates(any());
        answerDates(gemini, b -> read("Oct 9, 2026, 11:00 AM"));

        BarcodeResult result = service.decodeBatch(photos(1)).get(0);

        assertEquals("2026-10-09", result.getCreateDate());
    }

    @Test
    void aDateIsNotTrustedWhenTheReceiptPrintsADifferentWaybill() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ReceiptDateStore store = mock(ReceiptDateStore.class);
        BarcodeDecoderService service = new BarcodeDecoderService(
                zxing, gemini, Runnable::run, TODAY, Optional.of(store), Duration.ofSeconds(60));

        decodeWaybillFromFirstByte(zxing);
        answerDates(gemini, b -> Map.of(
                1, GeminiVisionService.ReceiptDateRead.found("Oct 5, 2026, 04:14 PM", "J09999999999"),
                2, GeminiVisionService.ReceiptDateRead.found("Oct 6, 2026, 10:07 AM", "j 0140-0310462"),
                3, GeminiVisionService.ReceiptDateRead.found("Oct 7, 2026, 08:00 AM", "")).get(b));

        List<BarcodeResult> results = service.decodeBatch(photos(1, 2, 3));

        assertEquals(null, results.get(0).getCreateDate());
        assertEquals("WAYBILL_MISMATCH", results.get(0).getCreateDateProblem());
        assertEquals("Oct 5, 2026, 04:14 PM", results.get(0).getCreateDateText());
        assertEquals("2026-10-06", results.get(1).getCreateDate());
        assertEquals("2026-10-07", results.get(2).getCreateDate());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ReceiptDateStore.SavedDate>> saved = ArgumentCaptor.forClass(Map.class);
        verify(store).saveDates(saved.capture());
        assertEquals(Set.of(waybill(2), waybill(3)), saved.getValue().keySet());
    }

    @Test
    void aMisreadBarcodeIsCorrectedWhenScanningAgainFindsThePrintedWaybill() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        ReceiptDateStore store = mock(ReceiptDateStore.class);
        BarcodeDecoderService service = new BarcodeDecoderService(
                zxing, gemini, Runnable::run, TODAY, Optional.of(store), Duration.ofSeconds(60));

        // The creased bottom barcode passes its checksum with two wrong symbols; the top one reads right
        when(zxing.decode(any(byte[].class))).thenReturn(List.of(
                new Result("J00521590777", null, null, BarcodeFormat.CODE_128)));
        when(zxing.decodeEveryCandidate(any(byte[].class))).thenReturn(Set.of("J00521590777", "J01421594677"));
        answerDates(gemini, b -> GeminiVisionService.ReceiptDateRead.found("Oct 6, 2026, 10:07 AM", "J01421594677"));

        List<BarcodeResult> results = service.decodeBatch(photos(1, 1));

        for (BarcodeResult result : results) {
            assertEquals("J01421594677", result.getDecodedValue());
            assertEquals(List.of("J01421594677"), result.getAllExtractedValues());
            assertEquals("2026-10-06", result.getCreateDate());
            assertEquals(null, result.getCreateDateProblem());
            assertEquals(null, result.getPrintedWaybill());
        }
        verify(zxing, times(1)).decodeEveryCandidate(any(byte[].class));
        verify(store).saveDates(Map.of(
                "J01421594677", new ReceiptDateStore.SavedDate(LocalDate.of(2026, 10, 6), "Oct 6, 2026, 10:07 AM")));
    }

    @Test
    void anUnconfirmedPrintedWaybillIsKeptWithItsDateForTheReconciliation() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = serviceWithToday(zxing, gemini);

        when(zxing.decode(any(byte[].class))).thenReturn(List.of(
                new Result("J00521590777", null, null, BarcodeFormat.CODE_128)));
        when(zxing.decodeEveryCandidate(any(byte[].class))).thenReturn(Set.of("J00521590777"));
        answerDates(gemini, b -> GeminiVisionService.ReceiptDateRead.found("Oct 6, 2026, 10:07 AM", "J01421594677"));

        BarcodeResult result = service.decodeBatch(photos(1)).get(0);

        assertEquals("J00521590777", result.getDecodedValue());
        assertEquals(null, result.getCreateDate());
        assertEquals("WAYBILL_MISMATCH", result.getCreateDateProblem());
        assertEquals("J01421594677", result.getPrintedWaybill());
        assertEquals("2026-10-06", result.getPrintedWaybillCreateDate());
    }

    @Test
    void doesNotReadDatesFromImagesWithoutABarcode() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = new BarcodeDecoderService(zxing, gemini, Runnable::run);
        MockMultipartFile image = new MockMultipartFile("images", "blank.png", "image/png", new byte[]{1});

        when(zxing.decode(any(byte[].class))).thenReturn(Collections.emptyList());
        when(gemini.extractBarcodesParallel(any())).thenReturn(List.of(Collections.emptyList()));

        BarcodeResult result = service.decodeBatch(List.of(image)).get(0);

        assertEquals(false, result.isSuccess());
        assertEquals(null, result.getCreateDateProblem());
        verify(gemini, never()).extractReceiptCreatedBatch(any());
    }

    @Test
    void usedUpDailyQuotaIsReportedAsSuchAndNotRetried() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = serviceWithToday(zxing, gemini);

        decodeWaybillFromFirstByte(zxing);
        answerDates(gemini, b -> GeminiVisionService.ReceiptDateRead.quotaUsedUp());

        BarcodeResult result = service.decodeBatch(photos(1)).get(0);

        assertEquals("QUOTA_EXHAUSTED", result.getCreateDateProblem());
        requestSizes(gemini, 1);
    }
}
