package com.excel.reconciler.controller;

import com.excel.reconciler.model.ReconciliationResponse;
import com.excel.reconciler.service.ReconciliationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class BarcodeReconciliationController {

    private final ReconciliationService reconciliationService;

    public BarcodeReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
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
            @RequestParam(value = "highlightFullRow", required = false, defaultValue = "false") boolean highlightFullRow,
            @RequestHeader(value = "X-Gemini-API-Key", required = false) String headerApiKey,
            @RequestParam(value = "geminiApiKey", required = false) String paramApiKey) {

        try {
            if (excelFile == null || excelFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Excel file (.xlsx or .xls) is required"));
            }

            List<MultipartFile> safeImages = (images != null) ? images : Collections.emptyList();
            String effectiveApiKey = (paramApiKey != null && !paramApiKey.isBlank()) ? paramApiKey : headerApiKey;

            ReconciliationResponse response = reconciliationService.reconcile(
                    excelFile,
                    safeImages,
                    columnName,
                    highlightFullRow,
                    effectiveApiKey
            );

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reconcile Excel and barcodes: " + e.getMessage()));
        }
    }
}
