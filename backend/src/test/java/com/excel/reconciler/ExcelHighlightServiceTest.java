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
}
