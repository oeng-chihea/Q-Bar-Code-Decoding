package com.excel.reconciler.service;

import com.excel.reconciler.model.BarcodeResult;
import com.excel.reconciler.model.ReconciliationResponse;
import com.excel.reconciler.util.SpreadsheetFileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

@Service
public class ReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final BarcodeDecoderService barcodeDecoderService;
    private final ExcelHighlightService excelHighlightService;
    private final ExcelImageExtractorService excelImageExtractorService;

    public ReconciliationService(BarcodeDecoderService barcodeDecoderService,
                                 ExcelHighlightService excelHighlightService,
                                 ExcelImageExtractorService excelImageExtractorService) {
        this.barcodeDecoderService = barcodeDecoderService;
        this.excelHighlightService = excelHighlightService;
        this.excelImageExtractorService = excelImageExtractorService;
    }

    public ReconciliationResponse reconcile(MultipartFile excelFile,
                                           List<MultipartFile> imageFiles,
                                           String columnName,
                                           boolean highlightFullRow) throws Exception {
        long startTime = System.currentTimeMillis();

        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException(SpreadsheetFileValidator.ERROR_MESSAGE);
        }

        SpreadsheetFileValidator.requireSupported(excelFile);

        ExcelImageExtractorService.ExtractedExcelData extracted =
                excelImageExtractorService.processExcelImage(excelFile);
        byte[] workbookBytes = extracted.getExcelBytes();
        if (workbookBytes == null || workbookBytes.length == 0) {
            throw new IllegalArgumentException(
                    "The uploaded Excel table image could not be converted into a spreadsheet.");
        }
        String excelSourceType = "EXCEL_TABLE_IMAGE";

        // 1. Decode all images in parallel
        List<BarcodeResult> scanResults = barcodeDecoderService.decodeBatch(imageFiles);

        // 2. Aggregate all barcodes and SKUs across all uploaded images/sheets
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

        // 3. Highlight matches in the uploaded Excel spreadsheet
        ExcelHighlightService.ExcelProcessingResult excelResult;

        try (InputStream is = new ByteArrayInputStream(workbookBytes)) {
            excelResult = excelHighlightService.highlightMatches(is, allDecodedCodes, columnName, highlightFullRow);
        }

        // 4. Calculate unmatched codes
        Set<String> unmatchedCodes = new LinkedHashSet<>();
        Set<String> matchedCodesSet = excelResult.getMatchedCodes();
        for (String decoded : allDecodedCodes) {
            boolean matched = false;
            if (matchedCodesSet.contains(decoded)) {
                matched = true;
            } else {
                String normDecoded = ExcelHighlightService.normalize(decoded);
                for (String m : matchedCodesSet) {
                    String normM = ExcelHighlightService.normalize(m);
                    if (normDecoded.equals(normM)
                            || (normDecoded.length() == 11 && normM.length() == 12 && normM.endsWith(normDecoded))
                            || (normDecoded.length() == 12 && normM.length() == 11 && normDecoded.endsWith(normM))) {
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) {
                unmatchedCodes.add(decoded);
            }
        }

        // 5. Build response
        String base64Excel = Base64.getEncoder().encodeToString(excelResult.getModifiedExcelBytes());
        String originalName = excelFile.getOriginalFilename() != null ? excelFile.getOriginalFilename() : "spreadsheet.xlsx";
        String downloadName = originalName.replaceFirst("(?i)\\.(xlsx|xls|csv|png|jpg|jpeg|webp)$", "")
                + "_highlighted.xlsx";

        long executionTimeMs = System.currentTimeMillis() - startTime;

        ReconciliationResponse response = new ReconciliationResponse();
        response.setTotalImages(imageFiles != null ? imageFiles.size() : 0);
        response.setDecodedImagesCount(decodedImagesCount);
        response.setExcelTotalRows(excelResult.getTotalRows());
        response.setMatchedRowsCount(excelResult.getMatchedRowsCount());
        response.setUnmatchedImagesCount(unmatchedCodes.size());
        response.setMatchedColumnName(excelResult.getResolvedColumnName());
        response.setMatchedColumnConfidence(excelResult.getMatchedColumnConfidence());
        response.setIdentifierColumnIndexes(excelResult.getIdentifierColumnIndexes());
        response.setActiveSheetName(excelResult.getActiveSheetName());
        response.setColumns(excelResult.getColumnHeaders());
        response.setScanResults(scanResults);
        response.setAllDecodedCodes(allDecodedCodes);
        response.setMatchedCodes(excelResult.getMatchedCodes());
        response.setUnmatchedCodes(unmatchedCodes);
        response.setPreviewRows(excelResult.getPreviewRows());
        response.setHighlightedExcelBase64(base64Excel);
        response.setDownloadFileName(downloadName);
        response.setExcelSourceType(excelSourceType);
        response.setExecutionTimeMs(executionTimeMs);

        log.info("Reconciliation complete: {} images scanned, {} decoded, {} matched in Excel (Sheet '{}') in {}ms",
                response.getTotalImages(), decodedImagesCount, excelResult.getMatchedRowsCount(),
                excelResult.getActiveSheetName(), executionTimeMs);

        return response;
    }
}
