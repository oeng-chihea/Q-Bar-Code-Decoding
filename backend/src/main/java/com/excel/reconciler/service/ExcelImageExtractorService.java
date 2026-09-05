package com.excel.reconciler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
public class ExcelImageExtractorService {
    private static final Logger log = LoggerFactory.getLogger(ExcelImageExtractorService.class);
    private final OllamaVisionService ollamaVisionService;
    private final ObjectMapper objectMapper;

    public static final String BARCODE_REJECTION_MESSAGE =
            "Barcode images are not supported in the Excel section. The Excel upload supports spreadsheet files (.xlsx, .xls, .csv) or images of an Excel table (.png, .jpg, .jpeg, .webp). Please upload barcode images in Step 2.";

    public static class ExtractedExcelData {
        private final boolean isExcelTable;
        private final boolean isBarcodeImage;
        private final String rejectionReason;
        private final String sheetName;
        private final List<String> headers;
        private final List<List<String>> rows;
        private final byte[] excelBytes;

        public ExtractedExcelData(boolean isExcelTable, boolean isBarcodeImage, String rejectionReason,
                                  String sheetName, List<String> headers, List<List<String>> rows,
                                  byte[] excelBytes) {
            this.isExcelTable = isExcelTable;
            this.isBarcodeImage = isBarcodeImage;
            this.rejectionReason = rejectionReason;
            this.sheetName = sheetName;
            this.headers = headers;
            this.rows = rows;
            this.excelBytes = excelBytes;
        }

        public boolean isExcelTable() {
            return isExcelTable;
        }

        public boolean isBarcodeImage() {
            return isBarcodeImage;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }

        public String getSheetName() {
            return sheetName;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public List<List<String>> getRows() {
            return rows;
        }

        public byte[] getExcelBytes() {
            return excelBytes;
        }
    }

    private static class OllamaExtractionResponse {
        final ExtractedExcelData data;
        final String errorMessage;
        final int lastStatusCode;

        OllamaExtractionResponse(ExtractedExcelData data) {
            this.data = data;
            this.errorMessage = null;
            this.lastStatusCode = 200;
        }

        OllamaExtractionResponse(String errorMessage, int lastStatusCode) {
            this.data = null;
            this.errorMessage = errorMessage;
            this.lastStatusCode = lastStatusCode;
        }
    }

    public ExcelImageExtractorService(OllamaVisionService ollamaVisionService, ObjectMapper objectMapper) {
        this.ollamaVisionService = ollamaVisionService;
        this.objectMapper = objectMapper;
    }

    public boolean isImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;
        String ct = file.getContentType();
        if (ct != null && ct.toLowerCase().startsWith("image/")) {
            return true;
        }
        String name = file.getOriginalFilename();
        if (name != null) {
            String lower = name.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".tif")
                    || lower.endsWith(".tiff");
        }
        return false;
    }

    /**
     * Process an image uploaded to the Excel slot.
     * Validates that the image is NOT a barcode image.
     * Extracts table content and creates an Apache POI workbook.
     */
    public ExtractedExcelData processExcelImage(MultipartFile file) throws Exception {
        byte[] imageBytes = file.getBytes();
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank() || !contentType.startsWith("image/")) {
            contentType = "image/jpeg";
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.png";

        OllamaExtractionResponse aiResult = extractWithOllama(imageBytes, contentType);
        if (aiResult.data != null) {
            if (aiResult.data.isBarcodeImage()) {
                log.info("Ollama classified image {} as a barcode image, rejecting", filename);
                throw new IllegalArgumentException(aiResult.data.getRejectionReason() != null
                        ? aiResult.data.getRejectionReason() : BARCODE_REJECTION_MESSAGE);
            }
            if (!aiResult.data.isExcelTable()) {
                throw new IllegalArgumentException(aiResult.data.getRejectionReason() != null
                        ? aiResult.data.getRejectionReason()
                        : "The uploaded image does not appear to be an Excel table or spreadsheet. Please upload an Excel file (.xlsx, .xls) or an image of an Excel spreadsheet.");
            }
            return aiResult.data;
        }

        log.error("Ollama vision extraction failed for file {}: status {}, msg: {}",
                filename, aiResult.lastStatusCode, aiResult.errorMessage);
        throw new IllegalArgumentException("Ollama vision extraction failed (" + aiResult.lastStatusCode + "): "
                + (aiResult.errorMessage != null ? aiResult.errorMessage : "Please verify that Ollama is running and qwen3-vl:8b-instruct is installed."));
    }

    private OllamaExtractionResponse extractWithOllama(byte[] imageBytes, String mimeType) {
        String prompt = """
                You are an expert OCR document digitizer and tabular data extractor for inventory and logistics reconciliation.
                Carefully inspect the provided image of the spreadsheet or table.

                CRITICAL EXTRACTION REQUIREMENTS:
                1. Extract ALL column header names into "headers".
                2. Extract EVERY SINGLE visible data row from top to bottom into "rows". Do not omit or summarize rows.
                3. Each row MUST contain a "values" array with one string per header, in exact visual column order.
                4. Copy every visible cell exactly as displayed, including Khmer and English text. Keep the original Unicode characters and punctuation.
                5. Do not translate or infer a human-readable value from a barcode, outlet code, product code, or project-specific mapping.
                6. Preserve IDs, barcodes, dates, numbers, punctuation, and blank cells exactly as displayed. Use an empty string when a cell is genuinely blank or unreadable; never invent text. Include a row even when some or all of its cells are blank.
                7. Keep each cell in its visual row and column position, including blank cells between populated cells.
                8. If this is an Excel sheet, logistics report, inventory list, or data table, set "isExcelTable" to true and "isBarcodeImage" to false.
                9. Only if this is purely a barcode sticker, product box with no table, or shipping label photo, set "isExcelTable" to false and "isBarcodeImage" to true.
                """;

        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "isExcelTable", Map.of("type", "boolean"),
                        "isBarcodeImage", Map.of("type", "boolean"),
                        "rejectionReason", Map.of("type", "string"),
                        "sheetName", Map.of("type", "string"),
                        "headers", Map.of("type", "array", "items", Map.of("type", "string")),
                        "rows", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "values", Map.of("type", "array", "items", Map.of("type", "string"))
                                        ),
                                        "required", List.of("values")
                                )
                        )
                ),
                "required", List.of("isExcelTable", "isBarcodeImage", "headers", "rows")
        );

        OllamaVisionService.JsonResponse response = ollamaVisionService.generateJson(
                imageBytes, mimeType, prompt, schema);
        if (response.isSuccessful()) {
            ExtractedExcelData parsed = parseOllamaResponse(response.content());
            if (parsed != null) {
                log.info("Successfully digitized Excel table using Ollama {}: {} headers, {} rows",
                        ollamaVisionService.getConfiguredModel(), parsed.getHeaders().size(), parsed.getRows().size());
                return new OllamaExtractionResponse(parsed);
            }
            return new OllamaExtractionResponse("Ollama returned JSON that could not be parsed", response.statusCode());
        }

        return new OllamaExtractionResponse(response.errorMessage(), response.statusCode());
    }

    public ExtractedExcelData parseOllamaResponse(String rawJson) {
        try {
            String clean = rawJson;
            if (clean.startsWith("```")) {
                int firstNl = clean.indexOf('\n');
                int lastBt = clean.lastIndexOf("```");
                if (firstNl != -1 && lastBt > firstNl) {
                    clean = clean.substring(firstNl + 1, lastBt).trim();
                }
            }

            JsonNode node = objectMapper.readTree(clean);
            boolean isExcelTable = node.path("isExcelTable").asBoolean(false);
            boolean isBarcodeImage = node.path("isBarcodeImage").asBoolean(false);
            String rejection = node.path("rejectionReason").asText(null);

            List<String> headers = new ArrayList<>();
            JsonNode headersNode = node.path("headers");
            if (headersNode.isArray()) {
                for (JsonNode h : headersNode) {
                    headers.add(h.asText(""));
                }
            }

            // Extract all data rows using multi-format parser
            List<List<String>> rows = parseAnyRowsFormat(node, headers);

            // If headers were missing but rows are key-value maps, infer headers from the first object
            if (headers.isEmpty() && !rows.isEmpty()) {
                JsonNode rowsArray = findRowsNode(node);
                if (rowsArray != null && rowsArray.isArray() && !rowsArray.isEmpty()) {
                    JsonNode firstRow = rowsArray.get(0);
                    if (firstRow.isObject() && !firstRow.has("values")) {
                        for (Iterator<String> it = firstRow.fieldNames(); it.hasNext(); ) {
                            headers.add(it.next());
                        }
                    }
                }
            }

            // CRITICAL OVERRIDE: If table headers or rows were extracted, it is definitely an Excel table!
            boolean hasTableData = (!headers.isEmpty() && headers.size() >= 2) || !rows.isEmpty();
            if (hasTableData) {
                isExcelTable = true;
                isBarcodeImage = false;
            }

            if (isBarcodeImage || !isExcelTable) {
                return new ExtractedExcelData(false, true,
                        rejection != null && !rejection.isBlank() ? rejection : BARCODE_REJECTION_MESSAGE,
                        null, Collections.emptyList(), Collections.emptyList(), null);
            }

            String sheetName = node.path("sheetName").asText("Scanned Inventory");
            if (sheetName.isBlank()) sheetName = "Scanned Inventory";

            if (headers.isEmpty()) {
                headers = List.of("Column 1", "Column 2", "Column 3");
            }

            validateExtractedRowWidths(headers, rows);

            // Keep every extracted row aligned to the semantic header order
            // before creating the workbook. This protects blank financial
            // columns from OCR row compaction.
            rows = normalizeExtractedRows(headers, rows);

            // Convert to Apache POI Workbook
            byte[] excelBytes = buildWorkbookBytes(sheetName, headers, rows);

            return new ExtractedExcelData(true, false, null, sheetName, headers, rows, excelBytes);
        } catch (Exception e) {
            log.warn("Rejected Ollama table response: {}", e.getMessage());
            return null;
        }
    }


    public List<List<String>> parseAnyRowsFormat(JsonNode node, List<String> headers) {
        List<List<String>> result = new ArrayList<>();
        JsonNode rowsNode = findRowsNode(node);
        if (rowsNode == null || !rowsNode.isArray()) {
            return result;
        }

        for (JsonNode rowNode : rowsNode) {
            List<String> cells = new ArrayList<>();

            if (rowNode.isArray()) {
                // Case 1: Row is a raw array of cell values ["val1", "val2", ...]
                for (JsonNode cell : rowNode) {
                    cells.add(cell.asText(""));
                }
            } else if (rowNode.isObject()) {
                // Case 2a: Row has an inner array under "values", "cells", "row", or "data"
                JsonNode innerArray = rowNode.has("values") ? rowNode.path("values")
                        : rowNode.has("cells") ? rowNode.path("cells")
                        : rowNode.has("row") ? rowNode.path("row")
                        : rowNode.has("data") ? rowNode.path("data") : null;

                if (innerArray != null && innerArray.isArray()) {
                    for (JsonNode cell : innerArray) {
                        cells.add(cell.asText(""));
                    }
                } else {
                    // Case 2b: Row is a map of column header -> value: {"Number": "1", "Waybill Number": "J01394871642", ...}
                    if (!headers.isEmpty()) {
                        for (String h : headers) {
                            JsonNode val = rowNode.get(h);
                            if (val == null) {
                                for (Iterator<String> it = rowNode.fieldNames(); it.hasNext(); ) {
                                    String fn = it.next();
                                    if (fn.equalsIgnoreCase(h)) {
                                        val = rowNode.get(fn);
                                        break;
                                    }
                                }
                            }
                            cells.add(val != null ? val.asText("") : "");
                        }
                    }
                    if (cells.isEmpty() || cells.stream().allMatch(String::isEmpty)) {
                        cells.clear();
                        for (Iterator<JsonNode> it = rowNode.elements(); it.hasNext(); ) {
                    cells.add(it.next().asText(""));
                        }
                    }
                }
            } else if (rowNode.isTextual()) {
                // Case 3: Row is a delimited string
                String text = rowNode.asText();
                if (!text.isBlank()) {
                    String[] parts = text.contains("\t") ? text.split("\t") : text.split(",");
                    for (String p : parts) {
                        cells.add(p);
                    }
                }
            }

            if (!cells.isEmpty()) {
                result.add(cells);
            }
        }
        return result;
    }

    private void validateExtractedRowWidths(List<String> headers, List<List<String>> rows) {
        int headerCount = headers == null ? 0 : headers.size();
        if (headerCount == 0 || rows == null) {
            return;
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row != null && row.size() > headerCount) {
                throw new IllegalArgumentException("OCR row " + rowIndex
                        + " contains " + row.size() + " cells but the table has only "
                        + headerCount + " headers");
            }
        }
    }

    /**
     * Repairs the specific row compaction produced by OCR for the logistics
     * sheet: an empty Return cell is sometimes represented as an empty Amount
     * cell, moving amount and commission one column to the right.
     */
    public List<List<String>> normalizeExtractedRows(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty() || rows == null || rows.isEmpty()) {
            return rows;
        }

        int amountIndex = headerIndex(headers, "amount payable to customers");
        int commissionIndex = headerIndex(headers, "commission");
        int returnIndex = headerIndex(headers, "retrun");
        if (returnIndex < 0) {
            returnIndex = headerIndex(headers, "return");
        }
        int codIndex = headerIndex(headers, "cod");
        final int resolvedReturnIndex = returnIndex;

        return rows.stream()
                .map(row -> normalizeExtractedRow(row, headers.size(), amountIndex, commissionIndex, resolvedReturnIndex, codIndex))
                .toList();
    }

    private List<String> normalizeExtractedRow(List<String> row, int headerCount,
                                               int amountIndex, int commissionIndex,
                                               int returnIndex, int codIndex) {
        List<String> aligned = new ArrayList<>();
        if (row != null) {
            aligned.addAll(row);
        }
        while (aligned.size() < headerCount) {
            aligned.add("");
        }
        if (aligned.size() > headerCount) {
            aligned = new ArrayList<>(aligned.subList(0, headerCount));
        }

        boolean hasFinancialLayout = amountIndex >= 0 && commissionIndex == amountIndex + 1
                && returnIndex == commissionIndex + 1 && codIndex == returnIndex + 1;
        if (hasFinancialLayout
                && isBlank(aligned.get(amountIndex))
                && isShiftedFinancialSequence(aligned.get(commissionIndex), aligned.get(returnIndex), aligned.get(codIndex))) {
            aligned.set(amountIndex, aligned.get(commissionIndex));
            aligned.set(commissionIndex, aligned.get(returnIndex));
            aligned.set(returnIndex, "");
        }

        return aligned;
    }

    private int headerIndex(List<String> headers, String expected) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i) != null && headers.get(i).trim().equalsIgnoreCase(expected)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isShiftedFinancialSequence(String amountCandidate, String commissionCandidate, String codCandidate) {
        Double amount = parseNumber(amountCandidate);
        Double commission = parseNumber(commissionCandidate);
        Double cod = parseNumber(codCandidate);
        if (amount == null || commission == null || cod == null) {
            return false;
        }

        // The COD value in this report is amount minus commission. That
        // relationship makes the repair safer than relying on fixed digit
        // lengths and also covers the 1,100 commission row in the reference.
        return amount >= 1000
                && commission >= 0
                && commission < amount
                && cod >= 1000
                && Math.abs((amount - commission) - cod) < 0.01;
    }

    private Double parseNumber(String value) {
        if (value == null) return null;
        String numeric = value.replace(",", "").replace("៛", "").replace("$", "").trim();
        try {
            return numeric.isEmpty() ? null : Double.parseDouble(numeric);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private JsonNode findRowsNode(JsonNode node) {
        String[] possibleKeys = {"rows", "data", "records", "table", "items", "content", "lines"};
        for (String k : possibleKeys) {
            JsonNode candidate = node.path(k);
            if (candidate.isArray() && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return node.path("rows");
    }

    public byte[] buildWorkbookBytes(String sheetName, List<String> headers, List<List<String>> rows) throws Exception {
        validateExtractedRowWidths(headers, rows);
        rows = normalizeExtractedRows(headers, rows);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName != null && !sheetName.isBlank() ? sheetName : "Inventory");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            if (headerStyle instanceof XSSFCellStyle xssfHeader) {
                byte[] rgb = new byte[]{(byte) 240, (byte) 242, (byte) 245};
                xssfHeader.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap()));
            } else {
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            }
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.BLACK.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);
            headerStyle.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());

            // Data Style
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            dataStyle.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            dataStyle.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            dataStyle.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

            // Create Header Row
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(headerStyle);
            }

            // Create Data Rows
            int rIdx = 1;
            for (List<String> rowData : rows) {
                Row row = sheet.createRow(rIdx++);
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.createCell(c);
                    String val = c < rowData.size() ? rowData.get(c) : "";
                    cell.setCellValue(val);
                    cell.setCellStyle(dataStyle);
                }
            }

            // Auto-size columns
            for (int c = 0; c < headers.size(); c++) {
                sheet.autoSizeColumn(c);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }
}
