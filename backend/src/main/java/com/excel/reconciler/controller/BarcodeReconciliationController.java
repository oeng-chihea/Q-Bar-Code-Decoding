package com.excel.reconciler.controller;

import com.excel.reconciler.model.BarcodeReconciliationRequest;
import com.excel.reconciler.model.BarcodeReconciliationStatusResponse;
import com.excel.reconciler.model.BarcodeReconciliationSubmissionResponse;
import com.excel.reconciler.model.BarcodeResult;
import com.excel.reconciler.model.ReconciliationResponse;
import com.excel.reconciler.model.ReconciliationStatus;
import com.excel.reconciler.model.UnmatchedImageDownload;
import com.excel.reconciler.model.UnmatchedImagesResponse;
import com.excel.reconciler.service.BarcodeReconciliationPublisher;
import com.excel.reconciler.service.BarcodeReconciliationRegistry;
import com.excel.reconciler.service.LocalReconciliationFileStorageService;
import com.excel.reconciler.util.SpreadsheetFileValidator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.lang.NonNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class BarcodeReconciliationController {

    @NonNull
    private static final MediaType XLSX_MEDIA_TYPE =
            Objects.requireNonNull(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private final BarcodeReconciliationPublisher reconciliationPublisher;
    private final BarcodeReconciliationRegistry reconciliationRegistry;
    private final LocalReconciliationFileStorageService fileStorage;

    public BarcodeReconciliationController(BarcodeReconciliationPublisher reconciliationPublisher,
                                           BarcodeReconciliationRegistry reconciliationRegistry,
                                           LocalReconciliationFileStorageService fileStorage) {
        this.reconciliationPublisher = reconciliationPublisher;
        this.reconciliationRegistry = reconciliationRegistry;
        this.fileStorage = fileStorage;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Excel Barcode Reconciler",
                "version", "1.0.0"
        ));
    }

    @PostMapping(value = "/barcodes/reconcile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> reconcile(
            @RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "columnName", required = false, defaultValue = "QR Barcode") String columnName,
            @RequestParam(value = "highlightFullRow", required = false, defaultValue = "false") boolean highlightFullRow) {

        try {
            if (excelFile == null || excelFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", SpreadsheetFileValidator.ERROR_MESSAGE));
            }

            SpreadsheetFileValidator.requireSupported(excelFile);
            List<MultipartFile> safeImages = images != null ? images : Collections.emptyList();

            String reconciliationId = UUID.randomUUID().toString();
            reconciliationRegistry.create(reconciliationId);

            try {
                Path excelPath = fileStorage.saveExcelFile(reconciliationId, excelFile);
                List<Path> imagePaths = fileStorage.saveImageFiles(reconciliationId, safeImages);
                reconciliationRegistry.setImagePaths(reconciliationId, imagePaths);

                BarcodeReconciliationRequest request = new BarcodeReconciliationRequest(
                        reconciliationId,
                        excelPath.toString(),
                        imagePaths.stream().map(Path::toString).toList(),
                        columnName,
                        highlightFullRow
                );

                reconciliationPublisher.publish(request);

                return ResponseEntity.accepted().body(new BarcodeReconciliationSubmissionResponse(
                        reconciliationId,
                        ReconciliationStatus.QUEUED,
                        "/api/v1/barcode-reconciliations/" + reconciliationId
                ));
            } catch (Exception e) {
                reconciliationRegistry.fail(reconciliationId, messageFor(e));
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "Could not queue barcode reconciliation: " + messageFor(e)));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to submit barcode reconciliation: " + messageFor(e)));
        }
    }

    @GetMapping("/barcode-reconciliations/{reconciliationId}")
    public ResponseEntity<?> status(@PathVariable String reconciliationId) {
        try {
            BarcodeReconciliationRegistry.Record record = reconciliationRegistry.require(reconciliationId);
            return ResponseEntity.ok(new BarcodeReconciliationStatusResponse(
                    record.reconciliationId(),
                    record.status(),
                    record.stage(),
                    record.errorMessage(),
                    record.status() == ReconciliationStatus.COMPLETED && record.result() != null,
                    record.downloadFileName()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/barcode-reconciliations/{reconciliationId}/result")
    public ResponseEntity<?> result(@PathVariable String reconciliationId) {
        try {
            BarcodeReconciliationRegistry.Record record = reconciliationRegistry.require(reconciliationId);
            if (record.status() != ReconciliationStatus.COMPLETED || record.result() == null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Reconciliation result is not ready",
                        "status", record.status().name()
                ));
            }
            return ResponseEntity.ok(record.result());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/barcode-reconciliations/{reconciliationId}/download")
    public ResponseEntity<?> download(@PathVariable String reconciliationId) {
        try {
            BarcodeReconciliationRegistry.Record record = reconciliationRegistry.require(reconciliationId);
            if (record.status() != ReconciliationStatus.COMPLETED) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Reconciliation result is not ready",
                        "status", record.status().name()
                ));
            }

            Path resultPath = record.resultPath();
            if (resultPath == null || !Files.isRegularFile(resultPath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Reconciliation workbook is not available"));
            }

            Resource resource = new FileSystemResource(resultPath);
            String downloadName = record.downloadFileName() != null
                    ? record.downloadFileName()
                    : "reconciliation_highlighted.xlsx";
            String disposition = ContentDisposition.attachment()
                    .filename(downloadName, StandardCharsets.UTF_8)
                    .build()
                    .toString();

            return ResponseEntity.ok()
                    .contentType(XLSX_MEDIA_TYPE)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/barcode-reconciliations/{reconciliationId}/download-unmatched")
    public ResponseEntity<?> downloadUnmatched(@PathVariable String reconciliationId) {
        try {
            BarcodeReconciliationRegistry.Record record = reconciliationRegistry.require(reconciliationId);
            if (record.status() != ReconciliationStatus.COMPLETED) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Reconciliation result is not ready",
                        "status", record.status().name()
                ));
            }

            ReconciliationResponse response = record.result();
            if (response == null || response.getScanResults() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Reconciliation scan results are not available"));
            }

            List<UnmatchedImageFile> unmatchedImages = findUnmatchedImages(reconciliationId, record, response);
            if (unmatchedImages.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No unmatched images found"));
            }

            List<UnmatchedImageDownload> downloads = unmatchedImages.stream()
                    .map(image -> new UnmatchedImageDownload(
                            image.imageIndex(),
                            image.filename(),
                            image.contentType(),
                            image.size(),
                            "/api/v1/barcode-reconciliations/" + reconciliationId
                                    + "/download-unmatched/" + image.imageIndex()))
                    .toList();

            return ResponseEntity.ok(new UnmatchedImagesResponse(downloads));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list unmatched images: " + messageFor(e)));
        }
    }

    @GetMapping("/barcode-reconciliations/{reconciliationId}/download-unmatched/{imageIndex}")
    public ResponseEntity<?> downloadUnmatchedImage(@PathVariable String reconciliationId,
                                                     @PathVariable int imageIndex) {
        try {
            BarcodeReconciliationRegistry.Record record = reconciliationRegistry.require(reconciliationId);
            if (record.status() != ReconciliationStatus.COMPLETED) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Reconciliation result is not ready",
                        "status", record.status().name()
                ));
            }

            ReconciliationResponse response = record.result();
            if (response == null || response.getScanResults() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Reconciliation scan results are not available"));
            }

            UnmatchedImageFile image = findUnmatchedImages(reconciliationId, record, response).stream()
                    .filter(candidate -> candidate.imageIndex() == imageIndex)
                    .findFirst()
                    .orElse(null);
            if (image == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Unmatched image not found"));
            }

            Resource resource = new FileSystemResource(image.path());
            String disposition = ContentDisposition.attachment()
                    .filename(image.filename(), StandardCharsets.UTF_8)
                    .build()
                    .toString();
            MediaType contentType = parseMediaTypeOrBinary(image.contentType());

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(image.size())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to download unmatched image: " + messageFor(e)));
        }
    }

    private List<UnmatchedImageFile> findUnmatchedImages(
            String reconciliationId,
            BarcodeReconciliationRegistry.Record record,
            ReconciliationResponse response) throws Exception {
        List<Path> imagePaths = record.imagePaths();
        if (imagePaths.isEmpty()) {
            imagePaths = fileStorage.getStoredImageFiles(reconciliationId);
        }

        List<UnmatchedImageFile> unmatchedImages = new ArrayList<>();
        List<BarcodeResult> scanResults = response.getScanResults();
        for (int i = 0; i < scanResults.size(); i++) {
            BarcodeResult item = scanResults.get(i);
            if (item == null || (item.isMatched() != null && item.isMatched())) {
                continue;
            }
            if (i >= imagePaths.size()) {
                continue;
            }

            Path imagePath = fileStorage.resolveStoredPath(imagePaths.get(i).toString());
            if (!Files.isRegularFile(imagePath)) {
                continue;
            }

            String filename = safeDownloadFilename(item.getFilename(), imagePath);
            unmatchedImages.add(new UnmatchedImageFile(
                    i,
                    imagePath,
                    filename,
                    fileStorage.contentTypeForPath(imagePath),
                    Files.size(imagePath)));
        }
        return unmatchedImages;
    }

    private String safeDownloadFilename(String originalFilename, Path imagePath) {
        String candidate = originalFilename;
        if (candidate == null || candidate.isBlank()) {
            candidate = imagePath.getFileName().toString();
        }
        int separatorIndex = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
        String basename = separatorIndex >= 0 ? candidate.substring(separatorIndex + 1) : candidate;
        basename = basename.replaceFirst("^\\d+-", "");
        String filename = basename.replaceAll("[^A-Za-z0-9._-]", "_");
        return filename.isBlank() || filename.equals(".") || filename.equals("..")
                ? "image.bin"
                : filename;
    }

    private MediaType parseMediaTypeOrBinary(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private record UnmatchedImageFile(int imageIndex,
                                      Path path,
                                      String filename,
                                      String contentType,
                                      long size) {
    }

    private String messageFor(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
