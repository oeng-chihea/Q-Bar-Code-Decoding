package com.excel.reconciler.controller;

import com.excel.reconciler.model.BarcodeReconciliationRequest;
import com.excel.reconciler.model.BarcodeReconciliationStatusResponse;
import com.excel.reconciler.model.BarcodeReconciliationSubmissionResponse;
import com.excel.reconciler.model.ReconciliationStatus;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class BarcodeReconciliationController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

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

    private String messageFor(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
