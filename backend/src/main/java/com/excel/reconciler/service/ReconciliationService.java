package com.excel.reconciler.service;

import com.excel.reconciler.model.BarcodeResult;
import com.excel.reconciler.model.ReconciliationResponse;
import com.excel.reconciler.model.UnmatchedWaybill;
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
    private final WaybillTrackingService waybillTrackingService;
    private final UnmatchedWaybillSheetWriter unmatchedWaybillSheetWriter;

    public ReconciliationService(BarcodeDecoderService barcodeDecoderService,
                                 ExcelHighlightService excelHighlightService,
                                 ExcelImageExtractorService excelImageExtractorService,
                                 WaybillTrackingService waybillTrackingService,
                                 UnmatchedWaybillSheetWriter unmatchedWaybillSheetWriter) {
        this.barcodeDecoderService = barcodeDecoderService;
        this.excelHighlightService = excelHighlightService;
        this.excelImageExtractorService = excelImageExtractorService;
        this.waybillTrackingService = waybillTrackingService;
        this.unmatchedWaybillSheetWriter = unmatchedWaybillSheetWriter;
    }

    public ReconciliationResponse reconcile(MultipartFile excelFile,
                                           List<MultipartFile> imageFiles,
                                           String columnName,
                                           boolean highlightFullRow,
                                           String reconciliationId) throws Exception {
        long startTime = System.currentTimeMillis();

        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException(SpreadsheetFileValidator.ERROR_MESSAGE);
        }

        SpreadsheetFileValidator.requireSupported(excelFile);

        ExcelImageExtractorService.ExtractedExcelData extracted =
                excelImageExtractorService.processExcelImage(excelFile);
        byte[] workbookBytes = extracted.getExcelBytes();
        if (workbookBytes == null || workbookBytes.length == 0 || extracted.getRows() == null || extracted.getRows().isEmpty()) {
            throw new IllegalArgumentException(ExcelImageExtractorService.NOT_AN_EXCEL_TABLE_MESSAGE);
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

        // 4. Align primary decoded value to matched code if present, and calculate unmatched codes
        Set<String> unmatchedCodes = new LinkedHashSet<>();
        Set<String> matchedImageCodes = new LinkedHashSet<>();
        Set<String> matchedCodesSet = excelResult.getMatchedCodes();
        int unmatchedImagesCount = 0;

        for (BarcodeResult res : scanResults) {
            if (!res.isSuccess()) {
                res.setMatched(false);
                unmatchedImagesCount++;
                continue;
            }

            List<String> candidates = new ArrayList<>();
            if (res.getDecodedValue() != null && !res.getDecodedValue().isBlank()) {
                candidates.add(res.getDecodedValue().trim());
            }
            if (res.getAllExtractedValues() != null) {
                for (String val : res.getAllExtractedValues()) {
                    if (val != null && !val.isBlank() && !candidates.contains(val.trim())) {
                        candidates.add(val.trim());
                    }
                }
            }

            String matchedCandidate = null;
            for (String candidate : candidates) {
                if (isCodeMatched(candidate, matchedCodesSet)) {
                    matchedCandidate = candidate;
                    break;
                }
            }

            if (matchedCandidate != null) {
                // Image matched an Excel row
                res.setDecodedValue(matchedCandidate);
                res.setMatched(true);
                matchedImageCodes.addAll(candidates);
            } else {
                // Image did not match any row in Excel
                res.setMatched(false);
                unmatchedImagesCount++;
                if (res.getDecodedValue() != null && !res.getDecodedValue().isBlank()) {
                    unmatchedCodes.add(res.getDecodedValue().trim());
                } else if (!candidates.isEmpty()) {
                    unmatchedCodes.add(candidates.get(0));
                }
            }
        }

        // 5. Track unmatched waybills over time and list the overdue ones in the downloaded workbook
        WaybillTrackingService.TrackingResult tracking =
                waybillTrackingService.track(unmatchedCodes, matchedImageCodes, reconciliationId);
        byte[] workbookWithTracking = appendUnmatchedSheet(excelResult.getModifiedExcelBytes(), tracking);

        // 6. Build response
        String base64Excel = Base64.getEncoder().encodeToString(workbookWithTracking);
        String originalFilename = excelFile.getOriginalFilename();
        String originalName = (originalFilename != null && !originalFilename.isBlank())
                ? originalFilename
                : "spreadsheet.xlsx";
        String downloadName = originalName.replaceFirst("(?i)\\.(xlsx|xls|csv|png|jpg|jpeg|webp)$", "")
                + "_highlighted.xlsx";

        long executionTimeMs = System.currentTimeMillis() - startTime;

        ReconciliationResponse response = new ReconciliationResponse();
        response.setTotalImages(imageFiles != null ? imageFiles.size() : 0);
        response.setDecodedImagesCount(decodedImagesCount);
        response.setExcelTotalRows(excelResult.getTotalRows());
        response.setMatchedRowsCount(excelResult.getMatchedRowsCount());
        response.setUnmatchedImagesCount(unmatchedImagesCount);
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
        response.setTrackingAvailable(tracking.available());
        response.setOverdueAfterDays(tracking.overdueAfterDays());
        response.setUnmatchedWaybills(tracking.waybills());
        response.setOverdueCount((int) tracking.overdueCount());

        log.info("Reconciliation complete: {} images scanned, {} decoded, {} matched in Excel (Sheet '{}') in {}ms",
                response.getTotalImages(), decodedImagesCount, excelResult.getMatchedRowsCount(),
                excelResult.getActiveSheetName(), executionTimeMs);

        return response;
    }

    private byte[] appendUnmatchedSheet(byte[] workbookBytes, WaybillTrackingService.TrackingResult tracking) {
        List<UnmatchedWaybill> waybills = tracking.waybills();
        try {
            return unmatchedWaybillSheetWriter.append(workbookBytes, waybills, tracking.overdueAfterDays());
        } catch (Exception e) {
            // The extra sheet is a convenience; never lose the highlighted workbook because of it
            log.warn("Could not add the unmatched waybills sheet to the workbook: {}", e.getMessage());
            return workbookBytes;
        }
    }

    private boolean isCodeMatched(String decoded, Set<String> matchedCodesSet) {
        if (decoded == null || decoded.isBlank() || matchedCodesSet == null || matchedCodesSet.isEmpty()) {
            return false;
        }
        if (matchedCodesSet.contains(decoded)) {
            return true;
        }
        String normDecoded = ExcelHighlightService.normalize(decoded);
        for (String m : matchedCodesSet) {
            String normM = ExcelHighlightService.normalize(m);
            if (normDecoded.equals(normM)
                    || (normDecoded.length() == 11 && normM.length() == 12 && normM.endsWith(normDecoded))
                    || (normDecoded.length() == 12 && normM.length() == 11 && normDecoded.endsWith(normM))) {
                return true;
            }
        }
        return false;
    }
}
