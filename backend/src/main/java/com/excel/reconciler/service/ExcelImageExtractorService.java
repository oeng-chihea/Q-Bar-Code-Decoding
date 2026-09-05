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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;

@Service
public class ExcelImageExtractorService {
    private static final Logger log = LoggerFactory.getLogger(ExcelImageExtractorService.class);
    private final GeminiVisionService geminiVisionService;
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

    private static class GeminiExtractionResponse {
        final ExtractedExcelData data;
        final String errorMessage;
        final int lastStatusCode;

        GeminiExtractionResponse(ExtractedExcelData data) {
            this.data = data;
            this.errorMessage = null;
            this.lastStatusCode = 200;
        }

        GeminiExtractionResponse(String errorMessage, int lastStatusCode) {
            this.data = null;
            this.errorMessage = errorMessage;
            this.lastStatusCode = lastStatusCode;
        }
    }

    public ExcelImageExtractorService(GeminiVisionService geminiVisionService, ObjectMapper objectMapper) {
        this.geminiVisionService = geminiVisionService;
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

        byte[] preprocessedBytes = preprocessTableImage(imageBytes, contentType);
        String preprocessedMime = (preprocessedBytes != imageBytes) ? "image/png" : contentType;

        GeminiExtractionResponse aiResult = extractWithGemini(preprocessedBytes, preprocessedMime);
        if (aiResult.data != null) {
            if (aiResult.data.isBarcodeImage()) {
                log.info("Gemini classified image {} as a barcode image, rejecting", filename);
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

        log.error("Gemini vision extraction failed for file {}: status {}, msg: {}",
                filename, aiResult.lastStatusCode, aiResult.errorMessage);
        throw new IllegalArgumentException("Gemini vision extraction failed (" + aiResult.lastStatusCode + "): "
                + (aiResult.errorMessage != null ? aiResult.errorMessage : "Please verify that the Gemini API key is valid and configured."));
    }

    /**
     * Upscales low-resolution table screenshots (e.g. 1024x484) where small Khmer glyphs
     * and subscripts are under 4px tall. High-quality bicubic interpolation increases stroke
     * clarity for Gemini vision patch tokens.
     */
    public byte[] preprocessTableImage(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return imageBytes;
        }
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                return imageBytes;
            }

            int origWidth = original.getWidth();
            int origHeight = original.getHeight();

            double scale = 1.0;
            if (origWidth < 1900 || origHeight < 1000) {
                double scaleW = 2048.0 / origWidth;
                double scaleH = 1000.0 / origHeight;
                scale = Math.max(scaleW, scaleH);
                scale = Math.min(scale, 2.5);
            }

            if (scale <= 1.05) {
                return imageBytes;
            }

            int targetWidth = (int) Math.round(origWidth * scale);
            int targetHeight = (int) Math.round(origHeight * scale);

            BufferedImage upscaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = upscaled.createGraphics();
            try {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, targetWidth, targetHeight);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
            } finally {
                g2d.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(upscaled, "png", baos);
            byte[] processedBytes = baos.toByteArray();
            log.info("Preprocessed table image: upscaled from {}x{} to {}x{} (scale: {:.2f}x, size: {} KB)",
                    origWidth, origHeight, targetWidth, targetHeight, scale, processedBytes.length / 1024);
            return processedBytes;
        } catch (Exception e) {
            log.warn("Table image preprocessing skipped: {}", e.getMessage());
            return imageBytes;
        }
    }

    private GeminiExtractionResponse extractWithGemini(byte[] imageBytes, String mimeType) {
        String prompt = """
                You are an expert OCR document digitizer and tabular data extractor for inventory and logistics reconciliation.
                Carefully inspect the provided image of the spreadsheet or table.

                CRITICAL EXTRACTION REQUIREMENTS:
                1. Extract ALL column header names into "headers".
                2. Extract EVERY SINGLE visible data row from top to bottom into "rows". Do not omit, skip, or summarize rows.
                3. Each row MUST contain a "values" array with one string per header, in exact visual column order.
                4. Khmer Script Accuracy: The document is a Cambodian logistics report written in authentic Khmer Unicode script (ភាសាខ្មែរ). Transcribe exact Khmer letters, consonants, vowels, and subscript consonants (e.g. សាខា សៀមរាប/REPDP01, ភ្នំពេញ, សាខាផ្សារ, អូរឫស្សី, ស្ទឹងមានជ័យ, សែនសុខ, ដង្កោ, ចំការមន, បឹងកេងកង, ទួលទំពូង, លេខកូដ...). STRICT PROHIBITION: NEVER use Thai characters (e.g. NEVER output เสียហាប, สาขา, or any Thai script) or Lao letters.
                5. CRITICAL ANTI-REPETITION RULE:
                   - For cells formatted with branch or outlet names as Khmer/CODE (e.g. text before and after '/'), transcribe both the exact Khmer name and the Latin code accurately (e.g. 'សាខា សៀមរាប/REPDP01').
                   - Destination columns ('POD Outlets') have DIFFERENT branches and codes across different rows. Read each cell independently based strictly on its own visual text. NEVER copy the origin branch or repeat the same branch across destination cells.
                6. Independent Cell Reading: Every cell across all columns and rows must be transcribed based strictly on its own visual text. Never carry over or assume text from neighboring or previous cells.
                7. Graceful Handling for Blurry Words: If any cell text is completely unreadable, blurry, faint, or cut off, output an empty string "" (or null) for that cell. Never guess, invent, or hallucinate text.
                8. Code & Number Fidelity: Preserve alphanumeric codes, outlet codes, waybills, IDs, dates, numbers, currency symbols, and slashes exactly as printed. Do not translate or alter codes.
                9. Preserve blank cells: Use an empty string "" for blank cells. Include every row even if some cells are blank.
                10. Maintain exact column alignment for each cell according to the headers.
                11. If this is an Excel sheet, logistics report, inventory list, or data table, set "isExcelTable" to true and "isBarcodeImage" to false.
                12. Only if this is purely a barcode sticker, product box with no table, or shipping label photo, set "isExcelTable" to false and "isBarcodeImage" to true.
                """;

        String systemInstruction = """
                You are an expert OCR document digitizer specialized in Cambodian logistics documents and authentic Khmer script.
                
                STRICT SCRIPT RULES:
                - ALWAYS transcribe using authentic Khmer Unicode characters (\\u1780-\\u17FF) and standard Latin/ASCII characters for alphanumeric codes and timestamps.
                - STRICTLY FORBIDDEN: NEVER output Thai characters (\\u0E00-\\u0E7F) or Lao characters.
                - Vowels and subscript consonants (ជើង) must follow standard Khmer orthography.
                - Common Cambodian logistics branch names:
                  * 'សាខា សៀមរាប' (Siem Reap, outlet code REP or REPDP01) - NEVER transcribe as 'เสีย', 'เสียហាប', or any Thai characters.
                  * 'សាខា ភ្នំពេញ' (Phnom Penh, outlet code PNH or PNHDP)
                  * 'សាខា បាត់ដំបង' (Battambang, outlet code BTB or BTBDP)
                  * 'សាខា ព្រះសីហនុ' (Sihanoukville, outlet code KPS, SHV)
                  * 'សាខា កំពង់ចាម' (Kampong Cham, outlet code KPC)
                  * 'សាខា កំពង់ឆ្នាំង' (Kampong Chhnang)
                  * 'សាខា កំពង់ស្ពឺ' (Kampong Speu)
                  * 'សាខា កំពង់ធំ' (Kampong Thom)
                  * 'សាខា កណ្តាល' (Kandal)
                  * 'សាខា តាកែវ' (Takeo)
                  * 'សាខា ស្វាយរៀង' (Svay Rieng)
                  * 'សាខា ព្រៃវែង' (Prey Veng)
                  * 'សាខា ពោធិ៍សាត់' (Pursat)
                  * 'សាខា ក្រចេះ' (Kratie)
                  * 'សាខា ស្ទឹងត្រែង' (Stung Treng)
                  * 'សាខា រតនគិរី' (Ratanakiri)
                  * 'សាខា មណ្ឌលគិរី' (Mondulkiri)
                  * 'សាខា ឧត្តរមានជ័យ' (Oddar Meanchey)
                  * 'សាខា បន្ទាយមានជ័យ' (Banteay Meanchey)
                  * 'សាខា ប៉ៃលិន' (Pailin)
                  * 'សាខា ព្រះវិហារ' (Preah Vihear)
                  * 'សាខា កោះកុង' (Koh Kong)
                  * 'សាខា កែប' (Kep)
                  * 'សាខា ត្បូងឃ្មុំ' (Tboung Khmum)
                """;

        Map<String, Object> schema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "isExcelTable", Map.of("type", "BOOLEAN"),
                        "isBarcodeImage", Map.of("type", "BOOLEAN"),
                        "rejectionReason", Map.of("type", "STRING"),
                        "sheetName", Map.of("type", "STRING"),
                        "headers", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                        "rows", Map.of(
                                "type", "ARRAY",
                                "items", Map.of(
                                        "type", "OBJECT",
                                        "properties", Map.of(
                                                "values", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))
                                        ),
                                        "required", List.of("values")
                                )
                        )
                ),
                "required", List.of("isExcelTable", "isBarcodeImage", "headers", "rows")
        );

        GeminiVisionService.JsonResponse response = geminiVisionService.generateJson(
                imageBytes, mimeType, prompt, schema);
        if (response.isSuccessful()) {
            ExtractedExcelData parsed = parseGeminiResponse(response.content());
            if (parsed != null) {
                String usedModel = (response.usedModel() != null && !response.usedModel().isBlank())
                        ? response.usedModel() : geminiVisionService.getConfiguredModel();
                log.info("Successfully digitized Excel table using Gemini {}: {} headers, {} rows",
                        usedModel, parsed.getHeaders().size(), parsed.getRows().size());
                return new GeminiExtractionResponse(parsed);
            }
            return new GeminiExtractionResponse("Gemini returned JSON that could not be parsed", response.statusCode());
        }

        return new GeminiExtractionResponse(response.errorMessage(), response.statusCode());
    }

    public ExtractedExcelData parseGeminiResponse(String rawJson) {
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
                    headers.add(sanitizeKhmerLogisticsText(h.asText("")));
                }
            }

            // Extract all data rows using multi-format parser
            List<List<String>> rawRows = parseAnyRowsFormat(node, headers);
            List<List<String>> rows = new ArrayList<>();
            for (List<String> rawRow : rawRows) {
                List<String> sanitizedRow = new ArrayList<>(rawRow.size());
                for (String cell : rawRow) {
                    sanitizedRow.add(sanitizeKhmerLogisticsText(cell));
                }
                rows.add(sanitizedRow);
            }

            // If headers were missing but rows are key-value maps, infer headers from the first object
            if (headers.isEmpty() && !rows.isEmpty()) {
                JsonNode rowsArray = findRowsNode(node);
                if (rowsArray != null && rowsArray.isArray() && !rowsArray.isEmpty()) {
                    JsonNode firstRow = rowsArray.get(0);
                    if (firstRow.isObject() && !firstRow.has("values")) {
                        for (Iterator<String> it = firstRow.fieldNames(); it.hasNext(); ) {
                            headers.add(sanitizeKhmerLogisticsText(it.next()));
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

            // Keep every extracted row aligned to the semantic header order
            // before validating row widths. This protects blank financial
            // columns from OCR row compaction and prevents trimming of the 11th column.
            rows = normalizeExtractedRows(headers, rows);
            validateExtractedRowWidths(headers, rows);

            // Convert to Apache POI Workbook
            byte[] excelBytes = buildWorkbookBytes(sheetName, headers, rows);

            return new ExtractedExcelData(true, false, null, sheetName, headers, rows, excelBytes);
        } catch (Exception e) {
            log.warn("Rejected Gemini table response: {}", e.getMessage());
            return null;
        }
    }

    public ExtractedExcelData parseOllamaResponse(String rawJson) {
        return parseGeminiResponse(rawJson);
    }

    /**
     * Sanitizes Cambodian logistics OCR text:
     * 1. Replaces known Thai OCR misreadings (e.g. เสียហាប -> សៀមរាប).
     * 2. Uses logistics outlet codes (REP/PNH/BTB/KPS/KPC) as authoritative ground-truth to correct corrupted branch names.
     * 3. Strips any remaining rogue Thai script glyphs (U+0E00-U+0E7F).
     */
    public String sanitizeKhmerLogisticsText(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        boolean hasThai = text.matches(".*[\\u0E00-\\u0E7F].*");
        boolean hasKnownMisreading = text.contains("เสีย") || text.contains("สาขา");
        boolean hasLogisticsSlash = text.contains("/") && (text.toUpperCase().contains("REP") || text.toUpperCase().contains("PNH")
                || text.toUpperCase().contains("BTB") || text.toUpperCase().contains("BDB") || text.toUpperCase().contains("KPS")
                || text.toUpperCase().contains("SHV") || text.toUpperCase().contains("KPC"));

        if (!hasThai && !hasKnownMisreading && !hasLogisticsSlash) {
            return text;
        }

        String cleaned = text.trim();

        // 1. Direct replacements for known Thai-script OCR hallucinations
        cleaned = cleaned.replace("เสียហាប", "សៀមរាប");
        cleaned = cleaned.replace("เสียមរាប", "សៀមរាប");
        cleaned = cleaned.replace("เสียមราบ", "សៀមរាប");
        cleaned = cleaned.replace("สาขา", "សាខា");
        cleaned = cleaned.replace("เสีย", "សៀ");

        // 2. Context-aware branch validation using destination/origin outlet code
        if (cleaned.contains("/")) {
            int slashIdx = cleaned.indexOf('/');
            String branchPart = cleaned.substring(0, slashIdx).trim();
            String codePart = cleaned.substring(slashIdx + 1).trim();
            String codeUpper = codePart.toUpperCase();

            if (codeUpper.contains("REP")) {
                // Siem Reap branch code
                if (branchPart.contains("សាខា") || branchPart.contains("សៀ") || branchPart.contains("រាប")
                        || branchPart.contains("ហាប") || branchPart.matches(".*[\\u0E00-\\u0E7F].*") || branchPart.isBlank()) {
                    return formatBranchWithPrefix(branchPart, "សៀមរាប") + "/" + codePart;
                }
            } else if (codeUpper.contains("PNH")) {
                // Phnom Penh branch code
                if (branchPart.contains("សាខា") || branchPart.contains("ភ្នំ") || branchPart.contains("ពេញ")
                        || branchPart.matches(".*[\\u0E00-\\u0E7F].*") || branchPart.isBlank()) {
                    return formatBranchWithPrefix(branchPart, "ភ្នំពេញ") + "/" + codePart;
                }
            } else if (codeUpper.contains("BTB") || codeUpper.contains("BDB")) {
                // Battambang branch code
                if (branchPart.contains("សាខា") || branchPart.contains("បាត់") || branchPart.contains("ដំបង")
                        || branchPart.matches(".*[\\u0E00-\\u0E7F].*") || branchPart.isBlank()) {
                    return formatBranchWithPrefix(branchPart, "បាត់ដំបង") + "/" + codePart;
                }
            } else if (codeUpper.contains("KPS") || codeUpper.contains("SHV")) {
                // Sihanoukville branch code
                if (branchPart.contains("សាខា") || branchPart.contains("ព្រះ") || branchPart.contains("សីហនុ")
                        || branchPart.matches(".*[\\u0E00-\\u0E7F].*") || branchPart.isBlank()) {
                    return formatBranchWithPrefix(branchPart, "ព្រះសីហនុ") + "/" + codePart;
                }
            } else if (codeUpper.contains("KPC")) {
                // Kampong Cham branch code
                if (branchPart.contains("សាខា") || branchPart.contains("កំពង់") || branchPart.contains("ចាម")
                        || branchPart.matches(".*[\\u0E00-\\u0E7F].*") || branchPart.isBlank()) {
                    return formatBranchWithPrefix(branchPart, "កំពង់ចាម") + "/" + codePart;
                }
            }
            cleaned = branchPart + "/" + codePart;
        }

        // 3. Remove any remaining stray Thai characters (U+0E00 to U+0E7F)
        if (cleaned.matches(".*[\\u0E00-\\u0E7F].*")) {
            cleaned = cleaned.replaceAll("[\\u0E00-\\u0E7F]", "");
        }

        return cleaned;
    }

    private String formatBranchWithPrefix(String branchPart, String branchName) {
        if (branchPart == null || branchPart.isBlank()) {
            return "សាខា " + branchName;
        }
        if (branchPart.contains("សាខា")) {
            if (branchPart.contains(" ") || branchPart.equals("សាខា")) {
                return "សាខា " + branchName;
            } else {
                return "សាខា" + branchName;
            }
        }
        return branchName;
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
                    cells.add(cell == null || cell.isNull() ? "" : cell.asText(""));
                }
            } else if (rowNode.isObject()) {
                // Case 2a: Row has an inner array under "values", "cells", "row", or "data"
                JsonNode innerArray = rowNode.has("values") ? rowNode.path("values")
                        : rowNode.has("cells") ? rowNode.path("cells")
                        : rowNode.has("row") ? rowNode.path("row")
                        : rowNode.has("data") ? rowNode.path("data") : null;

                if (innerArray != null && innerArray.isArray()) {
                    for (JsonNode cell : innerArray) {
                        cells.add(cell == null || cell.isNull() ? "" : cell.asText(""));
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
                            cells.add(val != null && !val.isNull() ? val.asText("") : "");
                        }
                    }
                    if (cells.isEmpty() || cells.stream().allMatch(String::isEmpty)) {
                        cells.clear();
                        for (Iterator<JsonNode> it = rowNode.elements(); it.hasNext(); ) {
                            JsonNode el = it.next();
                            cells.add(el == null || el.isNull() ? "" : el.asText(""));
                        }
                    }
                }
            } else if (rowNode.isTextual()) {
                // Case 3: Row is a delimited string
                String text = rowNode.asText("");
                if (!text.isBlank()) {
                    String[] parts = text.contains("\t") ? text.split("\t") : text.split(",");
                    for (String p : parts) {
                        cells.add(p != null ? p : "");
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
            if (row == null) {
                row = new ArrayList<>();
                rows.set(rowIndex, row);
            }
            while (row.size() < headerCount) {
                row.add("");
            }
            if (row.size() > headerCount) {
                List<String> trimmed = new ArrayList<>(row.subList(0, headerCount));
                rows.set(rowIndex, trimmed);
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
            headerFont.setFontName("Khmer OS Battambang");
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.BLACK.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);
            headerStyle.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());

            // Data Style
            CellStyle dataStyle = workbook.createCellStyle();
            Font dataFont = workbook.createFont();
            dataFont.setFontName("Khmer OS Battambang");
            dataFont.setFontHeightInPoints((short) 10);
            dataStyle.setFont(dataFont);
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
                String hVal = headers.get(c);
                cell.setCellValue(hVal != null ? hVal : "");
                cell.setCellStyle(headerStyle);
            }

            // Create Data Rows
            int rIdx = 1;
            for (List<String> rowData : rows) {
                Row row = sheet.createRow(rIdx++);
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.createCell(c);
                    String val = (rowData != null && c < rowData.size()) ? rowData.get(c) : "";
                    cell.setCellValue(val != null ? val : "");
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
