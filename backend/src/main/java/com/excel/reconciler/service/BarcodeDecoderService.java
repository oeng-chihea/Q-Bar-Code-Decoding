package com.excel.reconciler.service;

import com.excel.reconciler.model.BarcodeResult;
import com.google.zxing.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class BarcodeDecoderService {
    private static final Logger log = LoggerFactory.getLogger(BarcodeDecoderService.class);
    private static final int AI_FALLBACK_BATCH_SIZE = 4;

    private static final class LocalScan {
        private final String filename;
        private final byte[] imageBytes;
        private final String contentType;
        private final List<String> decodedValues;
        private final String barcodeFormat;
        private final String errorMessage;

        private LocalScan(String filename, byte[] imageBytes, String contentType,
                          List<String> decodedValues, String barcodeFormat, String errorMessage) {
            this.filename = filename;
            this.imageBytes = imageBytes;
            this.contentType = contentType;
            this.decodedValues = decodedValues;
            this.barcodeFormat = barcodeFormat;
            this.errorMessage = errorMessage;
        }

        private boolean hasDecodedValues() {
            return !decodedValues.isEmpty();
        }
    }

    private final ZXingDecoderService zxingDecoderService;
    private final GeminiVisionService geminiVisionService;
    private final Executor imageDecoderExecutor;
    private final Clock clock;
    // Wait before the second pass over date reads that failed; tests set it to 0
    private long retryCooldownMs = 4000;

    @Autowired
    public BarcodeDecoderService(ZXingDecoderService zxingDecoderService,
                                 GeminiVisionService geminiVisionService,
                                 @Qualifier("imageDecoderExecutor") Executor imageDecoderExecutor,
                                 @Value("${app.waybill.zone:Asia/Phnom_Penh}") String zone) {
        this(zxingDecoderService, geminiVisionService, imageDecoderExecutor, Clock.system(ZoneId.of(zone)));
    }

    BarcodeDecoderService(ZXingDecoderService zxingDecoderService,
                          GeminiVisionService geminiVisionService,
                          Executor imageDecoderExecutor,
                          Clock clock) {
        this.zxingDecoderService = zxingDecoderService;
        this.geminiVisionService = geminiVisionService;
        this.imageDecoderExecutor = imageDecoderExecutor;
        this.clock = clock;
    }

    void setRetryCooldownMs(long retryCooldownMs) {
        this.retryCooldownMs = retryCooldownMs;
    }

    BarcodeDecoderService(ZXingDecoderService zxingDecoderService,
                          GeminiVisionService geminiVisionService,
                          Executor imageDecoderExecutor) {
        this(zxingDecoderService, geminiVisionService, imageDecoderExecutor,
                Clock.system(ZoneId.of("Asia/Phnom_Penh")));
        this.retryCooldownMs = 0;
    }

    public List<BarcodeResult> decodeBatch(List<MultipartFile> imageFiles) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            return Collections.emptyList();
        }

        // Run the local decoder for every image in parallel before using any AI fallback.
        List<CompletableFuture<LocalScan>> futures = imageFiles.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> scanWithZxing(file), imageDecoderExecutor))
                .toList();

        List<LocalScan> localScans = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        int localDecodedCount = (int) localScans.stream().filter(LocalScan::hasDecodedValues).count();
        int fallbackCount = localScans.size() - localDecodedCount;
        log.info("ZXing decoded {}/{} barcode images; Gemini fallback required for {} images",
                localDecodedCount, localScans.size(), fallbackCount);

        // Preserve upload order while keeping only failed images for the AI stage.
        List<BarcodeResult> results = new ArrayList<>(Collections.nCopies(localScans.size(), null));
        List<Integer> fallbackIndexes = new ArrayList<>();
        for (int i = 0; i < localScans.size(); i++) {
            LocalScan localScan = localScans.get(i);
            if (localScan.hasDecodedValues()) {
                results.set(i, buildLocalResult(localScan));
            } else if (localScan.imageBytes.length == 0 && localScan.errorMessage != null) {
                results.set(i, buildFailedResult(localScan));
            } else {
                fallbackIndexes.add(i);
            }
        }

        // Batch only failed images with concurrent parallel execution.
        for (int start = 0; start < fallbackIndexes.size(); start += AI_FALLBACK_BATCH_SIZE) {
            int end = Math.min(start + AI_FALLBACK_BATCH_SIZE, fallbackIndexes.size());
            List<Integer> batchIndexes = fallbackIndexes.subList(start, end);
            List<GeminiVisionService.BarcodeImage> batchImages = batchIndexes.stream()
                    .map(index -> {
                        LocalScan scan = localScans.get(index);
                        return new GeminiVisionService.BarcodeImage(scan.imageBytes, scan.contentType);
                    })
                    .toList();
            List<List<String>> batchValues = geminiVisionService.extractBarcodesParallel(batchImages);

            for (int i = 0; i < batchIndexes.size(); i++) {
                int resultIndex = batchIndexes.get(i);
                List<String> values = i < batchValues.size() && batchValues.get(i) != null
                        ? batchValues.get(i)
                        : Collections.emptyList();
                results.set(resultIndex, buildGeminiResult(localScans.get(resultIndex), values));
            }
        }

        attachCreateDates(localScans, results);
        return results;
    }

    /**
     * Reads the receipt's CREATED date for every image whose barcode was decoded, and validates it.
     * Identical photos are read once and share the result. Reads that fail (rate limit, busy server)
     * get a second pass after a cool-down, and are reported as READ_FAILED rather than "no date on the
     * receipt". Images without a barcode are skipped because they cannot be matched to an Excel row.
     */
    private void attachCreateDates(List<LocalScan> localScans, List<BarcodeResult> results) {
        Map<String, List<Integer>> indexesByContent = new LinkedHashMap<>();
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).isSuccess() && localScans.get(i).imageBytes.length > 0) {
                indexesByContent.computeIfAbsent(contentHash(localScans.get(i).imageBytes), k -> new ArrayList<>())
                        .add(i);
            }
        }
        if (indexesByContent.isEmpty()) {
            return;
        }

        Map<String, GeminiVisionService.ReceiptDateRead> readings = readDates(
                localScans, indexesByContent, new ArrayList<>(indexesByContent.keySet()));

        List<String> failed = readings.entrySet().stream()
                .filter(entry -> entry.getValue().failed() && !entry.getValue().quotaExhausted())
                .map(Map.Entry::getKey)
                .toList();
        if (!failed.isEmpty()) {
            log.warn("Receipt date read failed for {} image(s); retrying them after a short pause", failed.size());
            pauseBeforeRetry();
            readings.putAll(readDates(localScans, indexesByContent, failed));
        }

        LocalDate today = LocalDate.now(clock);
        indexesByContent.forEach((hash, indexes) -> {
            GeminiVisionService.ReceiptDateRead reading = readings.get(hash);
            for (int index : indexes) {
                applyReading(results.get(index), reading, today);
            }
        });
    }

    /** Reads one photo per content hash, in the same small concurrent batches as the barcode fallback. */
    private Map<String, GeminiVisionService.ReceiptDateRead> readDates(List<LocalScan> localScans,
                                                                       Map<String, List<Integer>> indexesByContent,
                                                                       List<String> hashes) {
        Map<String, GeminiVisionService.ReceiptDateRead> readings = new LinkedHashMap<>();
        for (int start = 0; start < hashes.size(); start += AI_FALLBACK_BATCH_SIZE) {
            List<String> batch = hashes.subList(start, Math.min(start + AI_FALLBACK_BATCH_SIZE, hashes.size()));
            List<GeminiVisionService.BarcodeImage> images = batch.stream()
                    .map(hash -> {
                        LocalScan scan = localScans.get(indexesByContent.get(hash).get(0));
                        return new GeminiVisionService.BarcodeImage(scan.imageBytes, scan.contentType);
                    })
                    .toList();
            List<GeminiVisionService.ReceiptDateRead> batchReadings =
                    geminiVisionService.extractReceiptCreatedParallel(images);

            for (int i = 0; i < batch.size(); i++) {
                GeminiVisionService.ReceiptDateRead reading =
                        batchReadings != null && i < batchReadings.size() && batchReadings.get(i) != null
                                ? batchReadings.get(i)
                                : GeminiVisionService.ReceiptDateRead.failure();
                readings.put(batch.get(i), reading);
            }
        }
        return readings;
    }

    private void applyReading(BarcodeResult result, GeminiVisionService.ReceiptDateRead reading, LocalDate today) {
        if (reading.failed()) {
            result.setCreateDateProblem((reading.quotaExhausted()
                    ? ReceiptDateValidator.Problem.QUOTA_EXHAUSTED
                    : ReceiptDateValidator.Problem.READ_FAILED).name());
            return;
        }
        ReceiptDateValidator.Result validated = ReceiptDateValidator.validate(reading.text(), today);
        result.setCreateDateText(validated.rawText());
        if (validated.isValid()) {
            result.setCreateDate(validated.date().toString());
        } else {
            result.setCreateDateProblem(validated.problem().name());
        }
    }

    private void pauseBeforeRetry() {
        if (retryCooldownMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryCooldownMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String contentHash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private LocalScan scanWithZxing(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image_" + System.currentTimeMillis();
        try {
            byte[] bytes = file.getBytes();
            String contentType = file.getContentType();
            Set<String> decodedValues = new LinkedHashSet<>();
            String barcodeFormat = "UNKNOWN";

            List<Result> zxingResults = zxingDecoderService.decode(bytes);
            for (Result result : zxingResults) {
                if (result != null && result.getText() != null && !result.getText().trim().isEmpty()) {
                    decodedValues.add(result.getText().trim());
                    if (barcodeFormat.equals("UNKNOWN")) {
                        barcodeFormat = result.getBarcodeFormat().toString();
                    }
                }
            }

            // Kept even after a local decode: the receipt's CREATED date is still read from the image
            return new LocalScan(filename, bytes, contentType,
                    new ArrayList<>(decodedValues), barcodeFormat, null);
        } catch (Exception e) {
            log.error("Error processing file {} with ZXing: {}", filename, e.getMessage());
            return new LocalScan(filename, new byte[0], file.getContentType(),
                    Collections.emptyList(), "UNKNOWN", e.getMessage());
        }
    }

    private BarcodeResult buildLocalResult(LocalScan localScan) {
        String primary = pickPrimaryBarcode(localScan.decodedValues);
        log.debug("ZXing decoded {} value(s) from {}; skipping Gemini fallback",
                localScan.decodedValues.size(), localScan.filename);
        return new BarcodeResult(localScan.filename, primary, localScan.decodedValues,
                "ZXING", true, localScan.barcodeFormat, null);
    }

    private BarcodeResult buildGeminiResult(LocalScan localScan, List<String> geminiValues) {
        if (geminiValues != null && !geminiValues.isEmpty()) {
            String primary = pickPrimaryBarcode(geminiValues);
            return new BarcodeResult(localScan.filename, primary, geminiValues,
                    "GEMINI_AI", true, "AI_EXTRACTED", null);
        }

        return new BarcodeResult(localScan.filename, null, Collections.emptyList(),
                "FAILED", false, null, "Could not detect barcode or QR code");
    }

    private BarcodeResult buildFailedResult(LocalScan localScan) {
        return new BarcodeResult(localScan.filename, null, Collections.emptyList(),
                "FAILED", false, null, localScan.errorMessage);
    }

    private String pickPrimaryBarcode(List<String> values) {
        if (values == null || values.isEmpty()) return null;

        // 1. Prioritize alphanumeric tracking/waybill barcodes (e.g. J01396943696)
        for (String v : values) {
            if (v != null) {
                String trimmed = v.trim();
                if (trimmed.matches("^[A-Za-z]+[A-Za-z0-9_\\-]{7,25}$")) {
                    return trimmed;
                }
            }
        }

        // 2. Long numeric barcodes / waybills (length >= 10, e.g. 01400310465, EAN-13)
        for (String v : values) {
            if (v != null) {
                String trimmed = v.trim();
                if (trimmed.matches("^\\d{10,25}$")) {
                    return trimmed;
                }
            }
        }

        // 3. Medium alphanumeric codes / SKUs (length >= 6, e.g. SKU-9901, PROD-101)
        for (String v : values) {
            if (v != null) {
                String trimmed = v.trim();
                if (trimmed.matches("^[A-Za-z0-9_\\-]{6,25}$") && !trimmed.matches("^\\d{1,9}$")) {
                    return trimmed;
                }
            }
        }

        // 4. Shorter numeric barcodes (e.g. 6-9 digits like 00505718)
        for (String v : values) {
            if (v != null) {
                String trimmed = v.trim();
                if (trimmed.matches("^\\d{6,9}$")) {
                    return trimmed;
                }
            }
        }

        // 5. Fallback to first non-empty value
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return values.get(0).trim();
    }
}
