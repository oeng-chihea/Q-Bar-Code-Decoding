package com.excel.reconciler.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpreadsheetFileValidatorTest {

    @Test
    void acceptsOnlyNativeSpreadsheetExtensions() {
        assertTrue(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.xlsx", "application/octet-stream", new byte[]{1})));
        assertTrue(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.xls", "application/octet-stream", new byte[]{1})));
        assertTrue(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.csv", "text/csv", new byte[]{1})));
    }

    @Test
    void acceptsTableImageExtensionsRegardlessOfMimeType() {
        assertTrue(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.png", "application/octet-stream", new byte[]{1})));
        assertTrue(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.jpg", "application/vnd.ms-excel", new byte[]{1})));
        assertTrue(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.jpeg", "application/octet-stream", new byte[]{1})));
        assertTrue(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.webp", "application/octet-stream", new byte[]{1})));
    }

    @Test
    void acceptsImageMimeTypesEvenWhenTheFilenameLooksLikeExcel() {
        assertTrue(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.xlsx", "image/png", new byte[]{1})));
    }
}
