package com.excel.reconciler.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelExportLocalizationTest {

    @Test
    void preservesOutletCellsInPreviewAndDownloadedWorkbook() throws Exception {
        List<String> headers = List.of("Number", "Shipping Outlets", "POD Outlets");
        byte[] input;
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inventory");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("1");
            row.createCell(1).setCellValue("សាខាសៀមរាប/REPDP01");
            row.createCell(2).setCellValue("សាខាផ្សេង/BBMDP01");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            input = output.toByteArray();
        }

        ExcelHighlightService service = new ExcelHighlightService();
        ExcelHighlightService.ExcelProcessingResult result = service.highlightMatches(
                new ByteArrayInputStream(input), Set.of(), null, false
        );

        assertEquals(
                "សាខាសៀមរាប/REPDP01",
                result.getPreviewRows().get(0).getCells().get("Shipping Outlets")
        );
        assertEquals(
                "សាខាផ្សេង/BBMDP01",
                result.getPreviewRows().get(0).getCells().get("POD Outlets")
        );

        try (Workbook workbook = WorkbookFactoryCompat.open(result.getModifiedExcelBytes())) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertEquals("សាខាសៀមរាប/REPDP01", row.getCell(1).getStringCellValue());
            assertEquals("សាខាផ្សេង/BBMDP01", row.getCell(2).getStringCellValue());
        }
    }

    @Test
    void preservesRowsBeyondThePreviewLimitInTheDownloadedWorkbook() throws Exception {
        byte[] input;
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inventory");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Number");
            header.createCell(1).setCellValue("POD Outlets");
            for (int i = 1; i <= 501; i++) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue(Integer.toString(i));
                row.createCell(1).setCellValue("សាខាផ្សេង/BBMDP01");
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            input = output.toByteArray();
        }

        ExcelHighlightService service = new ExcelHighlightService();
        ExcelHighlightService.ExcelProcessingResult result = service.highlightMatches(
                new ByteArrayInputStream(input), Set.of(), null, false
        );

        try (Workbook workbook = WorkbookFactoryCompat.open(result.getModifiedExcelBytes())) {
            assertEquals(
                    "សាខាផ្សេង/BBMDP01",
                    workbook.getSheetAt(0).getRow(501).getCell(1).getStringCellValue()
            );
        }
    }

    private static final class WorkbookFactoryCompat {
        private static Workbook open(byte[] bytes) throws Exception {
            return org.apache.poi.ss.usermodel.WorkbookFactory.create(new ByteArrayInputStream(bytes));
        }
    }
}
