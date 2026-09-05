package com.excel.reconciler.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpreadsheetSchemaDetectorTest {

    private final SpreadsheetSchemaDetector detector = new SpreadsheetSchemaDetector();

    @Test
    void detectsAlphanumericCodeColumnUnderKhmerHeaderUsingDecodedOverlap() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inventory");
            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("របាយការណ៍ស្តុក");

            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("លេខរៀង");
            header.createCell(1).setCellValue("ឈ្មោះទំនិញ");
            header.createCell(2).setCellValue("លេខបាកូដ");

            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("1");
            data.createCell(1).setCellValue("កុំព្យូទ័រ");
            data.createCell(2).setCellValue("PROD-101");

            SpreadsheetSchemaDetector.Detection result = detector.detect(
                    sheet, new DataFormatter(), Set.of("PROD-101"), null);

            assertEquals(1, result.headerRowNum());
            assertEquals(List.of(2), result.identifierColumnIndexes());
            assertEquals("លេខបាកូដ", result.resolvedColumnName());
            assertTrue(result.confidence() >= 0.7);
        }
    }

    @Test
    void detectsNumericIdentifierColumnWhenHeaderHasNoKnownKeyword() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inventory");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ជួរ");
            header.createCell(1).setCellValue("ទំនិញ");
            header.createCell(2).setCellValue("ព័ត៌មាន");

            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("1");
            data.createCell(1).setCellValue("ផលិតផល");
            data.createCell(2).setCellValue("840192837401");

            SpreadsheetSchemaDetector.Detection result = detector.detect(
                    sheet, new DataFormatter(), Set.of("840192837401"), null);

            assertEquals(List.of(2), result.identifierColumnIndexes());
            assertEquals("ព័ត៌មាន", result.resolvedColumnName());
        }
    }
}
