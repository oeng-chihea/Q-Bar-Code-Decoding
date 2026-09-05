package com.excel.reconciler.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpreadsheetFileValidatorTest {

    @Test
    void rejectsNativeSpreadsheetExtensions() {
        assertFalse(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.xlsx", "application/octet-stream", new byte[]{1})));
        assertFalse(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.xls", "application/octet-stream", new byte[]{1})));
        assertFalse(SpreadsheetFileValidator.isSupported(
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

    @Test
    void rejectsUnsupportedFileTypes() {
        assertFalse(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.txt", "text/plain", new byte[]{1})));
        assertFalse(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "inventory.pdf", "application/pdf", new byte[]{1})));
        assertFalse(SpreadsheetFileValidator.isSupported(null));
        assertFalse(SpreadsheetFileValidator.isSupported(
                new MockMultipartFile("excelFile", "", "text/plain", new byte[0])));
    }

    @Test
    void requireSupportedThrowsExpectedMessageForInvalidFile() {
        MockMultipartFile invalidSpreadsheet = new MockMultipartFile("excelFile", "test.xlsx", "application/vnd.ms-excel", new byte[]{1});
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> SpreadsheetFileValidator.requireSupported(invalidSpreadsheet),
                SpreadsheetFileValidator.ERROR_MESSAGE
        );
    }
}
