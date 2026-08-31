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

        public ExcelProcessingResult(byte[] modifiedExcelBytes, int totalRows, int matchedRowsCount,
                                     String resolvedColumnName, String activeSheetName,
                                     List<String> columnHeaders, Set<String> matchedCodes,
                                     List<ExcelRowPreview> previewRows) {
            this.modifiedExcelBytes = modifiedExcelBytes;
            this.totalRows = totalRows;
            this.matchedRowsCount = matchedRowsCount;
            this.resolvedColumnName = resolvedColumnName;
            this.activeSheetName = activeSheetName;
            this.columnHeaders = columnHeaders;
            this.matchedCodes = matchedCodes;
            this.previewRows = previewRows;
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
    }

    private static class SheetHeaderInfo {
        int headerRowNum;
        int targetColIndex;
        String resolvedColName;
        List<String> headers;
        int score;
    }

    public ExcelProcessingResult highlightMatches(InputStream inputStream, Set<String> decodedCodes,
                                                 String preferredColumnName, boolean highlightFullRow) throws Exception {
        Workbook workbook = WorkbookFactory.create(inputStream);
        DataFormatter formatter = new DataFormatter();

        if (workbook.getNumberOfSheets() == 0) {
            throw new IllegalArgumentException("The uploaded Excel workbook contains no sheets");
        }

        // Prepare normalized lookup set of decoded codes
        Set<String> normalizedDecodedCodes = new HashSet<>();
        Map<String, String> normalizedToOriginal = new HashMap<>();
        for (String code : decodedCodes) {
            if (code != null && !code.trim().isEmpty()) {
                String norm = normalize(code);
                normalizedDecodedCodes.add(norm);
                normalizedToOriginal.put(norm, code);
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

        // Process all sheets in the workbook
        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sheet = workbook.getSheetAt(s);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) continue;

            SheetHeaderInfo headerInfo = findBestHeaderRow(sheet, formatter, preferredColumnName);
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

                // 1. Check all cells in the row for matches (to catch Barcode in Col 4 or SKU in Col 3)
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String val = formatter.formatCellValue(cell).trim();
                    String norm = normalize(val);
                    if (!norm.isEmpty() && normalizedDecodedCodes.contains(norm)) {
                        isMatch = true;
                        matchedValInRow = val;
                        cellsToHighlight.add(cell);
                    }
                }

                if (isMatch) {
                    sheetMatchedCount++;
                    String originalCode = normalizedToOriginal.get(normalize(matchedValInRow));
                    allMatchedCodes.add(originalCode != null ? originalCode : matchedValInRow);

                    if (highlightFullRow) {
                        for (int c = 0; c < headerInfo.headers.size(); c++) {
                            Cell cCell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                            cCell.setCellStyle(highlightStyle);
                        }
                    } else {
                        // Highlight all matched barcode/SKU cells in this row
                        for (Cell matchedCell : cellsToHighlight) {
                            matchedCell.setCellStyle(highlightStyle);
                        }
                    }
                }

                Cell targetCell = row.getCell(headerInfo.targetColIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String targetVal = formatter.formatCellValue(targetCell).trim();

                if (currentSheetPreviews.size() < 100) {
                    Map<String, String> cellData = new LinkedHashMap<>();
                    for (int c = 0; c < headerInfo.headers.size(); c++) {
                        Cell cCell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        String val = formatter.formatCellValue(cCell);
                        String colHeader = headerInfo.headers.get(c);
                        cellData.put(colHeader, val);
                    }
                    currentSheetPreviews.add(new ExcelRowPreview(r, cellData, isMatch ? matchedValInRow : targetVal, isMatch));
                }
            }

            totalDataRowsAcrossSheets += sheetDataRows;
            totalMatchedRowsAcrossSheets += sheetMatchedCount;

            // Pick the sheet with matches or highest catalog score as the primary preview sheet
            if (primarySheet == null || sheetMatchedCount > 0 || (primaryHeaderInfo != null && headerInfo.score > primaryHeaderInfo.score)) {
                primarySheet = sheet;
                primaryHeaderInfo = headerInfo;
                primaryPreviewRows = currentSheetPreviews;
            }
        }

        if (primarySheet == null) {
            primarySheet = workbook.getSheetAt(0);
            primaryHeaderInfo = new SheetHeaderInfo();
            primaryHeaderInfo.resolvedColName = preferredColumnName != null ? preferredColumnName : "QR Barcode";
            primaryHeaderInfo.headers = List.of("Column 1");
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
                primaryPreviewRows
        );
    }

    private SheetHeaderInfo findBestHeaderRow(Sheet sheet, DataFormatter formatter, String preferredColName) {
        String searchColName = (preferredColName != null && !preferredColName.trim().isEmpty())
                ? preferredColName.trim() : "QR Barcode";
        String normSearch = normalize(searchColName);

        SheetHeaderInfo bestInfo = null;
        int maxScanRows = Math.min(30, sheet.getLastRowNum() + 1);

        for (int r = 0; r < maxScanRows; r++) {
            Row row = sheet.getRow(r);
            if (row == null || row.getLastCellNum() <= 0) continue;

            List<String> headers = new ArrayList<>();
            int targetColIdx = -1;
            String resolvedCol = null;
            int score = 0;
            int validHeaderCount = 0;

            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String text = formatter.formatCellValue(cell).trim();

                // Skip formula definition text from being mistaken as a column header
                if (cell.getCellType() == CellType.FORMULA || text.startsWith("=") || text.contains("(") || text.contains("!")) {
                    headers.add("Column " + (c + 1));
                    continue;
                }

                headers.add(text.isEmpty() ? "Column " + (c + 1) : text);

                if (!text.isEmpty() && text.length() < 50) {
                    validHeaderCount++;
                    String normText = normalize(text);

                    // Exact or strong column match
                    if (text.equalsIgnoreCase(searchColName)) {
                        targetColIdx = c;
                        resolvedCol = text;
                        score += 200;
                    } else if (normText.contains("barcode") || normText.contains("qrcode") || normText.contains("code") || normText.contains(normSearch)) {
                        if (targetColIdx == -1) {
                            targetColIdx = c;
                            resolvedCol = text;
                        }
                        score += 100;
                    } else if (normText.contains("sku") || normText.contains("itemid") || normText.contains("productid") || normText.contains("model")) {
                        if (targetColIdx == -1) {
                            targetColIdx = c;
                            resolvedCol = text;
                        }
                        score += 40;
                    } else if (normText.contains("category") || normText.contains("name") || normText.contains("description") || normText.contains("price")) {
                        score += 15;
                    }
                }
            }

            if (targetColIdx != -1 && validHeaderCount >= 2) {
                SheetHeaderInfo info = new SheetHeaderInfo();
                info.headerRowNum = r;
                info.targetColIndex = targetColIdx;
                info.resolvedColName = resolvedCol;
                info.headers = headers;
                info.score = score + validHeaderCount;

                if (bestInfo == null || info.score > bestInfo.score) {
                    bestInfo = info;
                }
            }
        }

        return bestInfo;
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

    private String normalize(String str) {
        if (str == null) return "";
        return str.trim().replaceAll("[\\s_\\-/:()!']+", "").toLowerCase(Locale.ROOT);
    }
}
