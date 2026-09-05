package com.excel.reconciler.service;

import com.excel.reconciler.model.BarcodeResult;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

        BarcodeResult result = service.decodeSingleFile(image);

        assertTrue(result.isSuccess());
        assertEquals("01400310465", result.getDecodedValue());
        assertEquals("ZXING", result.getDecoderType());
        verifyNoInteractions(gemini);
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

        BarcodeResult result = service.decodeSingleFile(image);

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

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(gemini, times(2)).extractBarcodesParallel(captor.capture());
        assertEquals(List.of(4, 1), captor.getAllValues().stream().map(List::size).toList());
    }
}
