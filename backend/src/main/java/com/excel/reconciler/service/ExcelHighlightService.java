package com.excel.reconciler.service;

import com.excel.reconciler.model.ExcelRowPreview;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

@Service
public class ExcelHighlightService {
    private static final Logger log = LoggerFactory.getLogger(ExcelHighlightService.class);

    public static class ExcelProcessingResult {
        private final byte[] modifiedExcelBytes;
        private final int totalRows;
        private final int matchedRowsCount;
        private final String resolvedColumnName;
        private final String activeSheetName;
        private final List<String> columnHeaders;
        private final Set<String> matchedCodes;
        private final List<ExcelRowPreview> previewRows;
        private final double matchedColumnConfidence;
        private final List<Integer> identifierColumnIndexes;

        public ExcelProcessingResult(byte[] modifiedExcelBytes, int totalRows, int matchedRowsCount,
                                     String resolvedColumnName, String activeSheetName,
                                     List<String> columnHeaders, Set<String> matchedCodes,
                                     List<ExcelRowPreview> previewRows) {
            this(modifiedExcelBytes, totalRows, matchedRowsCount, resolvedColumnName, activeSheetName,
                    columnHeaders, matchedCodes, previewRows, 0.0, List.of());
        }

        public ExcelProcessingResult(byte[] modifiedExcelBytes, int totalRows, int matchedRowsCount,
                                     String resolvedColumnName, String activeSheetName,
                                     List<String> columnHeaders, Set<String> matchedCodes,
                                     List<ExcelRowPreview> previewRows, double matchedColumnConfidence,
                                     List<Integer> identifierColumnIndexes) {
            this.modifiedExcelBytes = modifiedExcelBytes;
            this.totalRows = totalRows;
            this.matchedRowsCount = matchedRowsCount;
            this.resolvedColumnName = resolvedColumnName;
            this.activeSheetName = activeSheetName;
            this.columnHeaders = columnHeaders;
            this.matchedCodes = matchedCodes;
            this.previewRows = previewRows;
            this.matchedColumnConfidence = matchedColumnConfidence;
            this.identifierColumnIndexes = identifierColumnIndexes == null
                    ? List.of() : List.copyOf(identifierColumnIndexes);
        }

        public byte[] getModifiedExcelBytes() {
            return modifiedExcelBytes;
        }

        public int getTotalRows() {
            return totalRows;
        }

        public int getMatchedRowsCount() {
            return matchedRowsCount;
        }

        public String getResolvedColumnName() {
            return resolvedColumnName;
        }

        public String getActiveSheetName() {
            return activeSheetName;
        }

        public List<String> getColumnHeaders() {
            return columnHeaders;
        }

        public Set<String> getMatchedCodes() {
            return matchedCodes;
        }

        public List<ExcelRowPreview> getPreviewRows() {
            return previewRows;
        }

        public double getMatchedColumnConfidence() {
            return matchedColumnConfidence;
        }

        public List<Integer> getIdentifierColumnIndexes() {
            return identifierColumnIndexes;
        }
    }

    private static class SheetHeaderInfo {
        int headerRowNum;
        int targetColIndex;
        String resolvedColName;
        List<String> headers;
        int score;
        double confidence;
        List<Integer> identifierColumnIndexes = List.of();
    }

    private final SpreadsheetSchemaDetector schemaDetector = new SpreadsheetSchemaDetector();

    public ExcelProcessingResult highlightMatches(InputStream inputStream, Set<String> decodedCodes,
                                                 String preferredColumnName, boolean highlightFullRow) throws Exception {
        Workbook workbook = WorkbookFactory.create(inputStream);
        DataFormatter formatter = new DataFormatter();

        if (workbook.getNumberOfSheets() == 0) {
            throw new IllegalArgumentException("The uploaded Excel workbook contains no sheets");
        }

        // Prepare decoded code lookup map (normalized -> original)
        Map<String, String> normalizedToOriginal = new LinkedHashMap<>();
        for (String code : decodedCodes) {
            if (code != null && !code.trim().isEmpty()) {
                String norm = normalize(code);
                if (!norm.isEmpty()) {
                    normalizedToOriginal.put(norm, code.trim());
                }
            }
        }

        // Create Highlight CellStyle
        CellStyle highlightStyle = createRedHighlightStyle(workbook);

        int totalDataRowsAcrossSheets = 0;
        int totalMatchedRowsAcrossSheets = 0;
        Set<String> allMatchedCodes = new LinkedHashSet<>();

        Sheet primarySheet = null;
        SheetHeaderInfo primaryHeaderInfo = null;
        List<ExcelRowPreview> primaryPreviewRows = new ArrayList<>();
        int bestSheetOverallScore = -1;

        // Process all sheets in the workbook
        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sheet = workbook.getSheetAt(s);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) continue;

            SpreadsheetSchemaDetector.Detection detection = schemaDetector.detect(
                    sheet, formatter, decodedCodes, preferredColumnName);
            SheetHeaderInfo headerInfo = toHeaderInfo(detection);
            if (headerInfo == null) continue;

            int sheetMatchedCount = 0;
            int sheetDataRows = 0;
            List<ExcelRowPreview> currentSheetPreviews = new ArrayList<>();

            for (int r = headerInfo.headerRowNum + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                sheetDataRows++;
                boolean isMatch = false;
                String matchedValInRow = "";
                List<Cell> cellsToHighlight = new ArrayList<>();

                // Match only the columns identified as identifiers. This
                // prevents a code printed inside a product description from
                // being treated as the row's barcode.
                for (int c : headerInfo.identifierColumnIndexes) {
                    if (c < 0 || c >= row.getLastCellNum()) continue;
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String val = formatter.formatCellValue(cell);
                    String norm = normalize(val);
                    if (norm.isEmpty()) continue;

                    String matchedDecodedOriginal = findMatchingDecodedCode(norm, normalizedToOriginal);
                    if (matchedDecodedOriginal != null) {
                        isMatch = true;
                        matchedValInRow = val;
                        cellsToHighlight.add(cell);
                        allMatchedCodes.add(matchedDecodedOriginal);
                    }
                }

                if (isMatch) {
                    sheetMatchedCount++;

                    if (highlightFullRow) {
                        for (int c = 0; c < headerInfo.headers.size(); c++) {
                            Cell cCell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                            cCell.setCellStyle(highlightStyle);
                        }
                    } else {
                        for (Cell matchedCell : cellsToHighlight) {
                            matchedCell.setCellStyle(highlightStyle);
                        }
                    }
                }

                Cell targetCell = row.getCell(headerInfo.targetColIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String targetVal = formatter.formatCellValue(targetCell);

                Map<String, String> cellData = new LinkedHashMap<>();
                for (int c = 0; c < headerInfo.headers.size(); c++) {
                    Cell cCell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String colHeader = headerInfo.headers.get(c);
                    String val = formatter.formatCellValue(cCell);
                    cellData.put(colHeader, val);
                }

                // Capture preview rows
                if (currentSheetPreviews.size() < 500) {
                    currentSheetPreviews.add(new ExcelRowPreview(r, cellData, isMatch ? matchedValInRow : targetVal, isMatch));
                }
            }

            totalDataRowsAcrossSheets += sheetDataRows;
            totalMatchedRowsAcrossSheets += sheetMatchedCount;

            // Prioritize the sheet that has the MOST MATCHED ITEMS for the UI preview
            int currentSheetScore = (sheetMatchedCount * 1000) + (sheetDataRows * 10) + headerInfo.score;

            if (primarySheet == null || currentSheetScore > bestSheetOverallScore) {
                primarySheet = sheet;
                primaryHeaderInfo = headerInfo;
                primaryPreviewRows = currentSheetPreviews;
                bestSheetOverallScore = currentSheetScore;
            }
        }

        if (primarySheet == null) {
            primarySheet = workbook.getSheetAt(0);
            primaryHeaderInfo = new SheetHeaderInfo();
            primaryHeaderInfo.resolvedColName = preferredColumnName != null ? preferredColumnName : "QR Barcode";
            primaryHeaderInfo.headers = List.of("Column 1");
            primaryHeaderInfo.confidence = 0.0;
            primaryHeaderInfo.identifierColumnIndexes = List.of(0);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return new ExcelProcessingResult(
                outputStream.toByteArray(),
                totalDataRowsAcrossSheets,
                totalMatchedRowsAcrossSheets,
                primaryHeaderInfo.resolvedColName,
                primarySheet.getSheetName(),
                primaryHeaderInfo.headers,
                allMatchedCodes,
                primaryPreviewRows,
                primaryHeaderInfo.confidence,
                primaryHeaderInfo.identifierColumnIndexes
        );
    }

    private SheetHeaderInfo toHeaderInfo(SpreadsheetSchemaDetector.Detection detection) {
        if (detection == null) return null;
        SheetHeaderInfo info = new SheetHeaderInfo();
        info.headerRowNum = detection.headerRowNum();
        info.targetColIndex = detection.identifierColumnIndexes().isEmpty()
                ? 0 : detection.identifierColumnIndexes().get(0);
        info.resolvedColName = detection.resolvedColumnName();
        info.headers = detection.headers();
        info.score = (int) Math.round(detection.score());
        info.confidence = detection.confidence();
        info.identifierColumnIndexes = detection.identifierColumnIndexes();
        return info;
    }

    /**
     * Intelligent matching between Excel cell value (normalized) and scanned decoded codes.
     * Supports exact match, Unicode/format canonicalization, UPC-A 11/12-digit
     * truncation alignment, EAN-13 padding, and a constrained numeric waybill
     * prefix variant.
     */
    public String findMatchingDecodedCode(String cellNorm, Map<String, String> normalizedToOriginal) {
        if (cellNorm == null || cellNorm.isEmpty()) return null;

        // 1. Direct exact normalized match
        if (normalizedToOriginal.containsKey(cellNorm)) {
            return normalizedToOriginal.get(cellNorm);
        }

        boolean cellIsDigits = cellNorm.matches("^\\d+$");
        String cellNoZeros = cellNorm.replaceFirst("^0+", "");

        for (Map.Entry<String, String> entry : normalizedToOriginal.entrySet()) {
            String decodedNorm = entry.getKey();
            String original = entry.getValue();

            // Direct equality
            if (cellNorm.equals(decodedNorm)) {
                return original;
            }

            boolean decodedIsDigits = decodedNorm.matches("^\\d+$");

            if (cellIsDigits && decodedIsDigits) {
                String decodedNoZeros = decodedNorm.replaceFirst("^0+", "");

                // Equal with leading zeros stripped (e.g. 0638201948512 vs 638201948512)
                if (!cellNoZeros.isEmpty() && cellNoZeros.equals(decodedNoZeros)) {
                    return original;
                }

                // UPC-A 11-digit scan vs 12-digit Excel (missing outer system digit or check digit)
                if (decodedNorm.length() == 11 && cellNorm.length() == 12) {
                    if (cellNorm.endsWith(decodedNorm) || cellNorm.startsWith(decodedNorm)) {
                        return original;
                    }
                }

                // 12-digit scan vs 11-digit Excel
                if (decodedNorm.length() == 12 && cellNorm.length() == 11) {
                    if (decodedNorm.endsWith(cellNorm) || decodedNorm.startsWith(cellNorm)) {
                        return original;
                    }
                }

                // EAN-13 (13 digits) vs UPC-A (12 digits)
                if (cellNorm.length() == 13 && decodedNorm.length() == 12) {
                    if (cellNorm.equals("0" + decodedNorm) || cellNorm.endsWith(decodedNorm)) {
                        return original;
                    }
                }
                if (decodedNorm.length() == 13 && cellNorm.length() == 12) {
                    if (decodedNorm.equals("0" + cellNorm) || decodedNorm.endsWith(cellNorm)) {
                        return original;
                    }
                }
            } else if (cellNorm.length() >= 5 && decodedNorm.length() >= 5) {
                // Some logistics systems add a letter prefix to an otherwise
                // numeric waybill (J01394871642 vs 01394871642). Do not use
                // broad suffix matching for two arbitrary alphanumeric IDs;
                // that can turn a description or a different SKU into a hit.
                if (isPrefixedNumeric(cellNorm, decodedNorm)
                        || isPrefixedNumeric(decodedNorm, cellNorm)) {
                    String prefixed = cellNorm.matches("^[a-z]+\\d+$") ? cellNorm : decodedNorm;
                    String numeric = cellNorm.matches("^\\d+$") ? cellNorm : decodedNorm;
                    String numericPart = prefixed.replaceFirst("^[a-z]+", "");
                    if (numericPart.equals(numeric)) {
                        return original;
                    }
                }
            }
        }

        return null;
    }

    private boolean isPrefixedNumeric(String first, String second) {
        return first != null && second != null
                && first.matches("^[a-z]+\\d+$")
                && second.matches("^\\d+$");
    }

    private CellStyle createRedHighlightStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        if (style instanceof XSSFCellStyle xssfStyle) {
            // Bright clear red highlight #FFB3B3 (RGB 255, 179, 179)
            byte[] rgb = new byte[]{(byte) 255, (byte) 179, (byte) 179};
            xssfStyle.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap()));
        } else {
            style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        }

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.RED.getIndex());
        style.setBottomBorderColor(IndexedColors.RED.getIndex());
        style.setLeftBorderColor(IndexedColors.RED.getIndex());
        style.setRightBorderColor(IndexedColors.RED.getIndex());

        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.DARK_RED.getIndex());
        style.setFont(font);

        return style;
    }

    public static String normalize(String str) {
        return IdentifierCanonicalizer.canonicalize(str == null ? null : str.trim());
    }
}
