package com.excel.reconciler.service;

import com.excel.reconciler.model.BarcodeResult;
import com.excel.reconciler.model.ReconciliationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
public class ReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final BarcodeDecoderService barcodeDecoderService;
    private final ExcelHighlightService excelHighlightService;

    public ReconciliationService(BarcodeDecoderService barcodeDecoderService,
                                 ExcelHighlightService excelHighlightService) {
        this.barcodeDecoderService = barcodeDecoderService;
        this.excelHighlightService = excelHighlightService;
    }

    public ReconciliationResponse reconcile(MultipartFile excelFile,
                                           List<MultipartFile> imageFiles,
                                           String columnName,
                                           boolean highlightFullRow,
                                           String geminiApiKey) throws Exception {
        long startTime = System.currentTimeMillis();

        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("Excel spreadsheet file is required");
        }

        // 1. Decode all images in parallel
        List<BarcodeResult> scanResults = barcodeDecoderService.decodeBatch(imageFiles, geminiApiKey);

        // 2. Aggregate all successfully decoded codes
        Set<String> allDecodedCodes = new LinkedHashSet<>();
        int decodedImagesCount = 0;

        for (BarcodeResult res : scanResults) {
            if (res.isSuccess()) {
                decodedImagesCount++;
                if (res.getDecodedValue() != null && !res.getDecodedValue().trim().isEmpty()) {
                    allDecodedCodes.add(res.getDecodedValue().trim());
                }
                if (res.getAllExtractedValues() != null) {
                    for (String val : res.getAllExtractedValues()) {
                        if (val != null && !val.trim().isEmpty()) {
                            allDecodedCodes.add(val.trim());
                        }
                    }
                }
            }
        }

        // 3. Highlight matches in Excel spreadsheet
        ExcelHighlightService.ExcelProcessingResult excelResult;
        try (InputStream is = excelFile.getInputStream()) {
            excelResult = excelHighlightService.highlightMatches(is, allDecodedCodes, columnName, highlightFullRow);
        }

        // 4. Calculate unmatched codes
        Set<String> unmatchedCodes = new LinkedHashSet<>();
        for (String decoded : allDecodedCodes) {
            boolean matched = false;
            String normDecoded = decoded.trim().replaceAll("[\\s_\\-/:()]+", "").toLowerCase(Locale.ROOT);
            for (String matchedCode : excelResult.getMatchedCodes()) {
                String normMatched = matchedCode.trim().replaceAll("[\\s_\\-/:()]+", "").toLowerCase(Locale.ROOT);
                if (normDecoded.equals(normMatched)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                unmatchedCodes.add(decoded);
            }
        }

        // 5. Build response
        String base64Excel = Base64.getEncoder().encodeToString(excelResult.getModifiedExcelBytes());
        String originalName = excelFile.getOriginalFilename() != null ? excelFile.getOriginalFilename() : "spreadsheet.xlsx";
        String downloadName = originalName.replaceFirst("\\.(xlsx|xls)$", "") + "_highlighted.xlsx";

        long executionTimeMs = System.currentTimeMillis() - startTime;

        ReconciliationResponse response = new ReconciliationResponse();
        response.setTotalImages(imageFiles != null ? imageFiles.size() : 0);
        response.setDecodedImagesCount(decodedImagesCount);
        response.setExcelTotalRows(excelResult.getTotalRows());
        response.setMatchedRowsCount(excelResult.getMatchedRowsCount());
        response.setUnmatchedImagesCount(unmatchedCodes.size());
        response.setMatchedColumnName(excelResult.getResolvedColumnName());
        response.setActiveSheetName(excelResult.getActiveSheetName());
        response.setColumns(excelResult.getColumnHeaders());
        response.setScanResults(scanResults);
        response.setAllDecodedCodes(allDecodedCodes);
        response.setMatchedCodes(excelResult.getMatchedCodes());
        response.setUnmatchedCodes(unmatchedCodes);
        response.setPreviewRows(excelResult.getPreviewRows());
        response.setHighlightedExcelBase64(base64Excel);
        response.setDownloadFileName(downloadName);
        response.setExecutionTimeMs(executionTimeMs);

        log.info("Reconciliation complete: {} images scanned, {} decoded, {} matched in Excel (Sheet '{}') in {}ms",
                response.getTotalImages(), decodedImagesCount, excelResult.getMatchedRowsCount(),
                excelResult.getActiveSheetName(), executionTimeMs);

        return response;
    }
}
