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

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class BarcodeDecoderService {
    private static final Logger log = LoggerFactory.getLogger(BarcodeDecoderService.class);
    private static final int AI_FALLBACK_BATCH_SIZE = 4;
    private static final Duration DEFAULT_DATE_READ_BUDGET = Duration.ofSeconds(60);
    private static final int DEFAULT_DATE_READ_BATCH_SIZE = 6;
    private static final int MAX_SAVED_WAYBILL_LENGTH = 64;
    private static final int MIN_PRINTED_WAYBILL_LENGTH = 6;

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
    private final Optional<ReceiptDetailStore> receiptStore;
    private final Duration dateReadBudget;
    private final int dateReadBatchSize;

    @Autowired
    public BarcodeDecoderService(ZXingDecoderService zxingDecoderService,
                                 GeminiVisionService geminiVisionService,
                                 @Qualifier("imageDecoderExecutor") Executor imageDecoderExecutor,
                                 Optional<ReceiptDetailStore> receiptStore,
                                 @Value("${app.waybill.zone:Asia/Phnom_Penh}") String zone,
                                 @Value("${gemini.api.date-read-budget-seconds:60}") int dateReadBudgetSeconds,
                                 @Value("${gemini.api.date-read-batch-size:6}") int dateReadBatchSize) {
        this(zxingDecoderService, geminiVisionService, imageDecoderExecutor, Clock.system(ZoneId.of(zone)),
                receiptStore, Duration.ofSeconds(Math.max(1, dateReadBudgetSeconds)), dateReadBatchSize);
    }

    BarcodeDecoderService(ZXingDecoderService zxingDecoderService,
                          GeminiVisionService geminiVisionService,
                          Executor imageDecoderExecutor,
                          Clock clock,
                          Optional<ReceiptDetailStore> receiptStore,
                          Duration dateReadBudget,
                          int dateReadBatchSize) {
        this.zxingDecoderService = zxingDecoderService;
        this.geminiVisionService = geminiVisionService;
        this.imageDecoderExecutor = imageDecoderExecutor;
        this.clock = clock;
        this.receiptStore = receiptStore;
        this.dateReadBudget = dateReadBudget;
        this.dateReadBatchSize = Math.max(1, dateReadBatchSize);
    }

    BarcodeDecoderService(ZXingDecoderService zxingDecoderService,
                          GeminiVisionService geminiVisionService,
                          Executor imageDecoderExecutor,
                          Clock clock,
                          Optional<ReceiptDetailStore> receiptStore,
                          Duration dateReadBudget) {
        this(zxingDecoderService, geminiVisionService, imageDecoderExecutor, clock, receiptStore, dateReadBudget,
                DEFAULT_DATE_READ_BATCH_SIZE);
    }

    BarcodeDecoderService(ZXingDecoderService zxingDecoderService,
                          GeminiVisionService geminiVisionService,
                          Executor imageDecoderExecutor,
                          Clock clock) {
        this(zxingDecoderService, geminiVisionService, imageDecoderExecutor, clock,
                Optional.empty(), DEFAULT_DATE_READ_BUDGET);
    }

    BarcodeDecoderService(ZXingDecoderService zxingDecoderService,
                          GeminiVisionService geminiVisionService,
                          Executor imageDecoderExecutor) {
        this(zxingDecoderService, geminiVisionService, imageDecoderExecutor,
                Clock.system(ZoneId.of("Asia/Phnom_Penh")));
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

    private void attachCreateDates(List<LocalScan> localScans, List<BarcodeResult> results) {
        Map<String, List<Integer>> indexesByWaybill = new LinkedHashMap<>();
        for (int i = 0; i < results.size(); i++) {
            BarcodeResult result = results.get(i);
            if (result.isSuccess() && result.getDecodedValue() != null && localScans.get(i).imageBytes.length > 0) {
                indexesByWaybill.computeIfAbsent(waybillKey(result.getDecodedValue()), k -> new ArrayList<>()).add(i);
            }
        }
        if (indexesByWaybill.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        Map<String, ReceiptDetailStore.SavedReceipt> saved = new LinkedHashMap<>();
        findSavedReceipts(indexesByWaybill.keySet()).forEach((waybill, receipt) -> {
            // A receipt saved before customer names were read is read once more
            if (receipt.isComplete()) {
                saved.put(waybill, receipt);
            }
        });
        saved.forEach((waybill, receipt) -> {
            for (int index : indexesByWaybill.getOrDefault(waybill, List.of())) {
                results.get(index).setCreateDateText(receipt.text());
                results.get(index).setCreateDate(receipt.date().toString());
                results.get(index).setCustomerName(blankToNull(receipt.customerName()));
            }
        });

        List<String> toRead = indexesByWaybill.keySet().stream().filter(waybill -> !saved.containsKey(waybill)).toList();
        if (toRead.isEmpty()) {
            log.info("Receipt dates: all {} reused from earlier uploads", saved.size());
            return;
        }

        List<List<String>> batches = new ArrayList<>();
        List<CompletableFuture<List<GeminiVisionService.ReceiptDateRead>>> reads = new ArrayList<>();
        for (int start = 0; start < toRead.size(); start += dateReadBatchSize) {
            List<String> batch = toRead.subList(start, Math.min(start + dateReadBatchSize, toRead.size()));
            List<GeminiVisionService.BarcodeImage> images = batch.stream()
                    .map(waybill -> {
                        LocalScan scan = localScans.get(indexesByWaybill.get(waybill).get(0));
                        return new GeminiVisionService.BarcodeImage(scan.imageBytes, scan.contentType);
                    })
                    .toList();
            batches.add(batch);
            reads.add(CompletableFuture.supplyAsync(
                    () -> geminiVisionService.extractReceiptCreatedBatch(images), imageDecoderExecutor));
        }

        awaitReads(reads);
        int unfinished = 0;
        Map<String, ReceiptDetailStore.SavedReceipt> newReceipts = new LinkedHashMap<>();
        for (int b = 0; b < batches.size(); b++) {
            CompletableFuture<List<GeminiVisionService.ReceiptDateRead>> read = reads.get(b);
            List<String> batch = batches.get(b);
            if (!read.isDone()) {
                unfinished += batch.size();
            }
            read.cancel(false);
            List<GeminiVisionService.ReceiptDateRead> readings = finishedReadings(read, batch.size());
            for (int i = 0; i < batch.size(); i++) {
                List<Integer> indexes = indexesByWaybill.get(batch.get(i));
                BarcodeResult readPhoto = results.get(indexes.get(0));
                correctMisreadBarcode(readings.get(i), localScans.get(indexes.get(0)), indexes, results);
                for (int index : indexes) {
                    ReceiptDetailStore.SavedReceipt receipt =
                            applyReading(results.get(index), readings.get(i), readPhoto, today);
                    if (receipt != null) {
                        newReceipts.putIfAbsent(waybillKey(readPhoto.getDecodedValue()), receipt);
                    }
                }
            }
        }
        log.info("Receipt dates: {} reused, {} read in {} request(s), {} not finished within {}s",
                saved.size(), toRead.size() - unfinished, batches.size(), unfinished, dateReadBudget.toSeconds());
        saveReceipts(newReceipts);
    }

    private void awaitReads(List<CompletableFuture<List<GeminiVisionService.ReceiptDateRead>>> reads) {
        try {
            CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new))
                    .get(dateReadBudget.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
        } catch (ExecutionException e) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<GeminiVisionService.ReceiptDateRead> finishedReadings(
            CompletableFuture<List<GeminiVisionService.ReceiptDateRead>> read, int size) {
        List<GeminiVisionService.ReceiptDateRead> readings =
                new ArrayList<>(Collections.nCopies(size, GeminiVisionService.ReceiptDateRead.failure()));
        if (!read.isDone() || read.isCancelled()) {
            return readings;
        }
        try {
            List<GeminiVisionService.ReceiptDateRead> answered = read.join();
            for (int i = 0; answered != null && i < Math.min(size, answered.size()); i++) {
                if (answered.get(i) != null) {
                    readings.set(i, answered.get(i));
                }
            }
        } catch (Exception e) {
            log.warn("Receipt date read error: {}", e.getMessage());
        }
        return readings;
    }

    private ReceiptDetailStore.SavedReceipt applyReading(BarcodeResult result, GeminiVisionService.ReceiptDateRead reading,
                                                         BarcodeResult readPhoto, LocalDate today) {
        if (reading.failed()) {
            result.setCreateDateProblem((reading.quotaExhausted()
                    ? ReceiptDateValidator.Problem.QUOTA_EXHAUSTED
                    : ReceiptDateValidator.Problem.READ_FAILED).name());
            return null;
        }
        if (!printedWaybillMatches(reading.waybillNo(), readPhoto)) {
            log.warn("Receipt date not trusted for {}: the receipt prints waybill {}",
                    readPhoto.getDecodedValue(), reading.waybillNo());
            result.setCreateDateText(reading.text());
            result.setCreateDateProblem(ReceiptDateValidator.Problem.WAYBILL_MISMATCH.name());
            // Kept so the reconciliation can still use the printed waybill when only it is in the Excel table
            ReceiptDateValidator.Result validated = ReceiptDateValidator.validate(reading.text(), today);
            result.setPrintedWaybill(reading.waybillNo().trim());
            result.setPrintedWaybillCustomerName(blankToNull(reading.customerName()));
            if (validated.isValid()) {
                result.setPrintedWaybillCreateDate(validated.date().toString());
            } else {
                result.setPrintedWaybillCreateDateProblem(validated.problem().name());
            }
            return null;
        }
        ReceiptDateValidator.Result validated = ReceiptDateValidator.validate(reading.text(), today);
        result.setCreateDateText(validated.rawText());
        result.setCustomerName(blankToNull(reading.customerName()));
        if (validated.isValid()) {
            result.setCreateDate(validated.date().toString());
        } else {
            result.setCreateDateProblem(validated.problem().name());
        }
        if (!validated.isValid() && result.getCustomerName() == null) {
            return null;
        }
        return new ReceiptDetailStore.SavedReceipt(
                validated.isValid() ? validated.date() : null, validated.rawText(), reading.customerName().trim());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * A receipt printing a different waybill than its barcode decoded to may mean the barcode was misread:
     * a creased label read from the downscaled photo can still pass the Code 128 checksum. The photo is
     * scanned again with every pass, and when the barcode also decodes to the printed waybill, two
     * independent reads agree, so that value replaces the misread one for every photo of the group.
     */
    private void correctMisreadBarcode(GeminiVisionService.ReceiptDateRead reading, LocalScan scan,
                                       List<Integer> indexes, List<BarcodeResult> results) {
        BarcodeResult readPhoto = results.get(indexes.get(0));
        if (reading.failed() || printedWaybillMatches(reading.waybillNo(), readPhoto)) {
            return;
        }
        String printed = normalizeCode(reading.waybillNo());
        Optional<String> confirmed = zxingDecoderService.decodeEveryCandidate(scan.imageBytes).stream()
                .filter(code -> normalizeCode(code).equals(printed))
                .findFirst();
        if (confirmed.isEmpty()) {
            return;
        }
        String misread = readPhoto.getDecodedValue();
        log.info("Barcode of {} re-read as {} instead of {}, matching the waybill printed on the receipt",
                scan.filename, confirmed.get(), misread);
        for (int index : indexes) {
            BarcodeResult result = results.get(index);
            List<String> values = new ArrayList<>();
            values.add(confirmed.get());
            if (result.getAllExtractedValues() != null) {
                result.getAllExtractedValues().stream()
                        .filter(value -> value != null && !value.equals(misread) && !value.equals(confirmed.get()))
                        .forEach(values::add);
            }
            result.setDecodedValue(confirmed.get());
            result.setAllExtractedValues(values);
        }
    }

    private static boolean printedWaybillMatches(String printedWaybill, BarcodeResult photo) {
        String printed = normalizeCode(printedWaybill);
        if (printed.length() < MIN_PRINTED_WAYBILL_LENGTH) {
            return true;
        }
        List<String> codes = new ArrayList<>();
        codes.add(photo.getDecodedValue());
        if (photo.getAllExtractedValues() != null) {
            codes.addAll(photo.getAllExtractedValues());
        }
        return codes.stream().anyMatch(code -> normalizeCode(code).equals(printed));
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static String waybillKey(String decodedValue) {
        return decodedValue.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, ReceiptDetailStore.SavedReceipt> findSavedReceipts(Collection<String> waybills) {
        if (receiptStore.isEmpty()) {
            return Map.of();
        }
        try {
            return receiptStore.get().findReceipts(waybills);
        } catch (Exception e) {
            log.warn("Could not load saved receipt details: {}", e.getMessage());
            return Map.of();
        }
    }

    private void saveReceipts(Map<String, ReceiptDetailStore.SavedReceipt> receipts) {
        if (receiptStore.isEmpty() || receipts.isEmpty()) {
            return;
        }
        Map<String, ReceiptDetailStore.SavedReceipt> storable = new LinkedHashMap<>();
        receipts.forEach((waybill, receipt) -> {
            if (waybill.length() <= MAX_SAVED_WAYBILL_LENGTH) {
                storable.put(waybill, receipt);
            }
        });
        try {
            receiptStore.get().saveReceipts(storable);
        } catch (Exception e) {
            log.warn("Could not save receipt details: {}", e.getMessage());
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
