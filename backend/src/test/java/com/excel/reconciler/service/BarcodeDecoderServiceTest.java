package com.excel.reconciler.service;

import com.excel.reconciler.model.BarcodeResult;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    private BarcodeDecoderService serviceWithToday(ZXingDecoderService zxing, GeminiVisionService gemini) {
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T05:00:00Z"), ZoneId.of("Asia/Phnom_Penh"));
        BarcodeDecoderService service = new BarcodeDecoderService(zxing, gemini, Runnable::run, clock);
        service.setRetryCooldownMs(0);
        return service;
    }

    private static GeminiVisionService.ReceiptDateRead read(String text) {
        return GeminiVisionService.ReceiptDateRead.found(text);
    }

    @Test
    void readsAndValidatesTheCreatedDateForEveryDecodedReceipt() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = serviceWithToday(zxing, gemini);
        List<MultipartFile> images = List.of(
                new MockMultipartFile("images", "ok.png", "image/png", new byte[]{1}),
                new MockMultipartFile("images", "missing.png", "image/png", new byte[]{2}),
                new MockMultipartFile("images", "future.png", "image/png", new byte[]{3})
        );

        when(zxing.decode(any(byte[].class))).thenReturn(Collections.singletonList(
                new Result("01400310465", null, null, BarcodeFormat.CODE_128)));
        when(gemini.extractReceiptCreatedParallel(any()))
                .thenReturn(List.of(read("Oct 18, 2026, 02:29 PM"), read(""), read("Dec 01, 2026, 09:00 AM")));

        List<BarcodeResult> results = service.decodeBatch(images);

        assertEquals("2026-10-18", results.get(0).getCreateDate());
        assertEquals(null, results.get(0).getCreateDateProblem());
        assertEquals(null, results.get(1).getCreateDate());
        assertEquals("NOT_FOUND", results.get(1).getCreateDateProblem());
        assertEquals("FUTURE_DATE", results.get(2).getCreateDateProblem());
        assertEquals("Dec 01, 2026, 09:00 AM", results.get(2).getCreateDateText());
    }

    @Test
    void identicalPhotosAreReadOnceAndShareTheResult() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = serviceWithToday(zxing, gemini);
        List<MultipartFile> images = List.of(
                new MockMultipartFile("images", "a.png", "image/png", new byte[]{1, 1}),
                new MockMultipartFile("images", "b.png", "image/png", new byte[]{2, 2}),
                new MockMultipartFile("images", "a-copy.png", "image/png", new byte[]{1, 1})
        );

        when(zxing.decode(any(byte[].class))).thenReturn(Collections.singletonList(
                new Result("01400310465", null, null, BarcodeFormat.CODE_128)));
        when(gemini.extractReceiptCreatedParallel(any()))
                .thenReturn(List.of(read("Oct 5, 2026, 04:14 PM"), read("Oct 6, 2026, 10:07 AM")));

        List<BarcodeResult> results = service.decodeBatch(images);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GeminiVisionService.BarcodeImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(gemini).extractReceiptCreatedParallel(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals("2026-10-05", results.get(0).getCreateDate());
        assertEquals("2026-10-06", results.get(1).getCreateDate());
        assertEquals("2026-10-05", results.get(2).getCreateDate());
    }

    @Test
    void failedReadsGetASecondPassAndOnlyTheStillFailingOnesAreReported() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = serviceWithToday(zxing, gemini);
        List<MultipartFile> images = List.of(
                new MockMultipartFile("images", "recovers.png", "image/png", new byte[]{1}),
                new MockMultipartFile("images", "stays-failed.png", "image/png", new byte[]{2}),
                new MockMultipartFile("images", "fine.png", "image/png", new byte[]{3})
        );

        when(zxing.decode(any(byte[].class))).thenReturn(Collections.singletonList(
                new Result("01400310465", null, null, BarcodeFormat.CODE_128)));
        when(gemini.extractReceiptCreatedParallel(any()))
                .thenReturn(List.of(GeminiVisionService.ReceiptDateRead.failure(),
                        GeminiVisionService.ReceiptDateRead.failure(), read("Oct 8, 2026, 01:00 PM")))
                .thenReturn(List.of(read("Oct 5, 2026, 12:25 PM"), GeminiVisionService.ReceiptDateRead.failure()));

        List<BarcodeResult> results = service.decodeBatch(images);

        verify(gemini, times(2)).extractReceiptCreatedParallel(any());
        assertEquals("2026-10-05", results.get(0).getCreateDate());
        assertEquals("READ_FAILED", results.get(1).getCreateDateProblem());
        assertEquals(null, results.get(1).getCreateDate());
        assertEquals("2026-10-08", results.get(2).getCreateDate());
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
        verify(gemini, never()).extractReceiptCreatedParallel(any());
    }

    @Test
    void usedUpDailyQuotaIsReportedAsSuchAndNotRetried() throws Exception {
        ZXingDecoderService zxing = mock(ZXingDecoderService.class);
        GeminiVisionService gemini = mock(GeminiVisionService.class);
        BarcodeDecoderService service = serviceWithToday(zxing, gemini);
        MockMultipartFile image = new MockMultipartFile("images", "a.png", "image/png", new byte[]{1});

        when(zxing.decode(any(byte[].class))).thenReturn(Collections.singletonList(
                new Result("01400310465", null, null, BarcodeFormat.CODE_128)));
        when(gemini.extractReceiptCreatedParallel(any()))
                .thenReturn(List.of(GeminiVisionService.ReceiptDateRead.quotaUsedUp()));

        BarcodeResult result = service.decodeBatch(List.of(image)).get(0);

        assertEquals("QUOTA_EXHAUSTED", result.getCreateDateProblem());
        verify(gemini, times(1)).extractReceiptCreatedParallel(any());
    }
}
