package com.excel.reconciler;

import com.excel.reconciler.service.ExcelHighlightService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ExcelHighlightServiceTest {

    private final ExcelHighlightService highlightService = new ExcelHighlightService();

    @Test
    public void testHighlightMatchingRows() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Inventory");

        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Item ID");
        header.createCell(1).setCellValue("QR Barcode");
        header.createCell(2).setCellValue("Item Name");

        // Row 1 (Match)
        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("101");
        row1.createCell(1).setCellValue("PROD-101");
        row1.createCell(2).setCellValue("Laptop");

        // Row 2 (No match)
        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("102");
        row2.createCell(1).setCellValue("PROD-102");
        row2.createCell(2).setCellValue("Monitor");

        // Row 3 (Match with leading zero)
        Row row3 = sheet.createRow(3);
        row3.createCell(0).setCellValue("103");
        row3.createCell(1).setCellValue("0098765");
        row3.createCell(2).setCellValue("Keyboard");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        byte[] inputExcelBytes = baos.toByteArray();

        Set<String> scannedCodes = Set.of("PROD-101", "0098765");

        ExcelHighlightService.ExcelProcessingResult result = highlightService.highlightMatches(
                new ByteArrayInputStream(inputExcelBytes),
                scannedCodes,
                "QR Barcode",
                false
        );

        assertEquals(3, result.getTotalRows());
        assertEquals(2, result.getMatchedRowsCount());
        assertEquals("QR Barcode", result.getResolvedColumnName());
        assertTrue(result.getMatchedCodes().contains("PROD-101"));
        assertTrue(result.getMatchedCodes().contains("0098765"));

        // Verify output workbook
        Workbook outputWb = WorkbookFactory.create(new ByteArrayInputStream(result.getModifiedExcelBytes()));
        Sheet outSheet = outputWb.getSheetAt(0);

        // Row 1 barcode cell has highlight style
        Cell cell1 = outSheet.getRow(1).getCell(1);
        assertNotNull(cell1.getCellStyle());
        assertEquals(FillPatternType.SOLID_FOREGROUND, cell1.getCellStyle().getFillPattern());

        outputWb.close();
    }

    @Test
    public void testUpcOuterDigitTruncationMatching() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Category Breakdown");

        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("No.");
        header.createCell(1).setCellValue("Item ID");
        header.createCell(2).setCellValue("Barcode");
        header.createCell(3).setCellValue("Item Name");

        // Row 1: Orange Tent (Excel has 12 digits: 638201948512)
        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("1");
        row1.createCell(1).setCellValue("OUT-110");
        row1.createCell(2).setCellValue("638201948512");
        row1.createCell(3).setCellValue("Ultralight Backpacking Tent (2P)");

        // Row 2: Smart Hub (Excel has 840192837418)
        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("2");
        row2.createCell(1).setCellValue("ELE-034");
        row2.createCell(2).setCellValue("840192837418");
        row2.createCell(3).setCellValue("Smart Home Hub");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        // 11-digit OCR scan (missing outer left 6) + 12-digit exact scan
        Set<String> scannedCodes = Set.of("38201948512", "840192837418");

        ExcelHighlightService.ExcelProcessingResult result = highlightService.highlightMatches(
                new ByteArrayInputStream(baos.toByteArray()),
                scannedCodes,
                "Barcode",
                false
        );

        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getMatchedRowsCount(), "Both 11-digit truncated UPC and 12-digit UPC should match");
        assertTrue(result.getMatchedCodes().contains("38201948512"));
        assertTrue(result.getMatchedCodes().contains("840192837418"));
    }

    @Test
    public void testDetectColumn3BarcodeTarget() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Category Breakdown");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("No.");
        header.createCell(1).setCellValue("Item ID");
        header.createCell(2).setCellValue("Column 3");
        header.createCell(3).setCellValue("Item Name");
        header.createCell(4).setCellValue("Category");

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("1");
        row1.createCell(1).setCellValue("HTL-012");
        row1.createCell(2).setCellValue("840192837401");
        row1.createCell(3).setCellValue("Handmade Ceramic Planter");
        row1.createCell(4).setCellValue("Home");

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("2");
        row2.createCell(1).setCellValue("ELE-034");
        row2.createCell(2).setCellValue("840192837418");
        row2.createCell(3).setCellValue("Smart Home Hub");
        row2.createCell(4).setCellValue("Electronics");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        Set<String> scannedCodes = Set.of("840192837418");

        ExcelHighlightService.ExcelProcessingResult result = highlightService.highlightMatches(
                new ByteArrayInputStream(baos.toByteArray()),
                scannedCodes,
                null,
                false
        );

        assertEquals(2, result.getTotalRows());
        assertEquals(1, result.getMatchedRowsCount());
        assertEquals("Column 3", result.getResolvedColumnName(), "Should detect Column 3 as the target column holding barcodes");
        assertTrue(result.getMatchedCodes().contains("840192837418"));
    }

    @Test
    public void matchesCodeUnderKhmerHeaderAndDoesNotMatchSameTextInDescription() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Inventory");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("ឈ្មោះទំនិញ");
        header.createCell(1).setCellValue("លេខបាកូដ");
        header.createCell(2).setCellValue("កំណត់សម្គាល់");

        Row matchingRow = sheet.createRow(1);
        matchingRow.createCell(0).setCellValue("កុំព្យូទ័រ");
        matchingRow.createCell(1).setCellValue("PROD-101");
        matchingRow.createCell(2).setCellValue("កូដផ្សេង");

        Row descriptionOnlyRow = sheet.createRow(2);
        descriptionOnlyRow.createCell(0).setCellValue("PROD-101");
        descriptionOnlyRow.createCell(1).setCellValue("PROD-999");
        descriptionOnlyRow.createCell(2).setCellValue("កូដផ្សេង");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        ExcelHighlightService.ExcelProcessingResult result = highlightService.highlightMatches(
                new ByteArrayInputStream(baos.toByteArray()),
                Set.of("PROD-101"),
                null,
                false
        );

        assertEquals("លេខបាកូដ", result.getResolvedColumnName());
        assertEquals(1, result.getMatchedRowsCount());
        assertTrue(result.getMatchedColumnConfidence() >= 0.7);
    }

    @Test
    public void doesNotUseBroadAlphanumericSuffixMatching() {
        assertNull(highlightService.findMatchingDecodedCode(
                "xabc123",
                java.util.Map.of("abc123", "ABC123")));
    }

    @Test
    public void preservesRawCellTextInPreviewWhileMatchingCanonicalValue() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Inventory");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("កូដ");
        header.createCell(1).setCellValue("ឈ្មោះ");
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("  PROD-101  ");
        row.createCell(1).setCellValue("  ទំនិញ  ");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        ExcelHighlightService.ExcelProcessingResult result = highlightService.highlightMatches(
                new ByteArrayInputStream(baos.toByteArray()),
                Set.of("PROD-101"), null, false);

        assertEquals("  PROD-101  ", result.getPreviewRows().get(0).getCells().get("កូដ"));
        assertEquals("  ទំនិញ  ", result.getPreviewRows().get(0).getCells().get("ឈ្មោះ"));
        assertEquals(1, result.getMatchedRowsCount());
    }
}
