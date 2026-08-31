package com.excel.reconciler.service;

import com.excel.reconciler.model.BarcodeResult;
import com.google.zxing.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class BarcodeDecoderService {
    private static final Logger log = LoggerFactory.getLogger(BarcodeDecoderService.class);

    private final ZXingDecoderService zxingDecoderService;
    private final GeminiVisionService geminiVisionService;
    private final Executor imageDecoderExecutor;

    public BarcodeDecoderService(ZXingDecoderService zxingDecoderService,
                                 GeminiVisionService geminiVisionService,
                                 @Qualifier("imageDecoderExecutor") Executor imageDecoderExecutor) {
        this.zxingDecoderService = zxingDecoderService;
        this.geminiVisionService = geminiVisionService;
        this.imageDecoderExecutor = imageDecoderExecutor;
    }

    public List<BarcodeResult> decodeBatch(List<MultipartFile> imageFiles, String geminiApiKey) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            return Collections.emptyList();
        }

        List<CompletableFuture<BarcodeResult>> futures = imageFiles.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> decodeSingleFile(file, geminiApiKey), imageDecoderExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    public BarcodeResult decodeSingleFile(MultipartFile file, String geminiApiKey) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image_" + System.currentTimeMillis();
        try {
            byte[] bytes = file.getBytes();
            String contentType = file.getContentType();

            // 1. Fast Local ZXing Decoding
            List<Result> zxingResults = zxingDecoderService.decode(bytes);
            if (!zxingResults.isEmpty()) {
                List<String> values = zxingResults.stream().map(Result::getText).distinct().toList();
                String primary = pickPrimaryBarcode(values);
                String format = zxingResults.get(0).getBarcodeFormat().toString();
                return new BarcodeResult(filename, primary, values, "ZXING", true, format, null);
            }

            // 2. Intelligent Gemini AI Vision Fallback
            List<String> geminiValues = geminiVisionService.extractBarcodesWithGemini(bytes, contentType, geminiApiKey);
            if (!geminiValues.isEmpty()) {
                String primary = pickPrimaryBarcode(geminiValues);
                return new BarcodeResult(filename, primary, geminiValues, "GEMINI_AI", true, "AI_EXTRACTED", null);
            }

            // 3. Failed to detect
            return new BarcodeResult(filename, null, Collections.emptyList(), "FAILED", false, null, "Could not detect barcode or QR code");
        } catch (Exception e) {
            log.error("Error processing file {}: {}", filename, e.getMessage());
            return new BarcodeResult(filename, null, Collections.emptyList(), "FAILED", false, null, e.getMessage());
        }
    }

    private String pickPrimaryBarcode(List<String> values) {
        if (values == null || values.isEmpty()) return null;

        // 1. Look for numeric barcode (digits only, length >= 6)
        for (String v : values) {
            if (v != null && v.matches("^\\d{6,18}$")) {
                return v.trim();
            }
        }

        // 2. Look for any value containing mostly digits
        for (String v : values) {
            if (v != null && v.matches(".*\\d{6,}.*")) {
                return v.trim();
            }
        }

        // 3. Fallback to first value
        return values.get(0).trim();
    }
}
