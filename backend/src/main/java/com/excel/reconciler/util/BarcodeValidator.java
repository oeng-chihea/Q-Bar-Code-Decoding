package com.excel.reconciler.util;

import java.util.Locale;
import java.util.Set;

/**
 * High-precision GS1 Barcode and SKU Validator.
 * Supports UPC-A, EAN-13, EAN-8, Code-128, Code-39, QR Codes, and Inventory SKU codes.
 */
public final class BarcodeValidator {

    private static final Set<String> STOP_WORDS = Set.of(
            "item", "sku", "barcode", "qrcode", "upc", "ean", "name", "product",
            "category", "price", "cost", "qty", "quantity", "shipto", "ship",
            "null", "none", "true", "false", "undefined"
    );

    private BarcodeValidator() {}

    /**
     * Normalizes a code string by removing spaces, hyphens, and non-alphanumeric noise.
     */
    public static String cleanCode(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("[\\s_\\-/:()!']+", "");
    }

    /**
     * Validates if a code string is a plausible barcode, QR code content, or SKU identifier.
     */
    public static boolean isValidCode(String raw) {
        if (raw == null) return false;
        String trimmed = raw.trim();
        if (trimmed.length() < 2 || trimmed.length() > 50) {
            return false;
        }

        String lowerClean = cleanCode(trimmed).toLowerCase(Locale.ROOT);
        if (STOP_WORDS.contains(lowerClean)) {
            return false;
        }

        // Pure digits: barcode number
        if (trimmed.matches("^\\d+$")) {
            return trimmed.length() >= 4 && trimmed.length() <= 32;
        }

        // Alphanumeric SKU / Item ID (e.g. OUT-110, ELE-034, PET-020, PWR-099, HTL_012)
        return trimmed.matches("^[A-Za-z0-9_\\-\\./#]+$");
    }

    /**
     * Validates if a barcode string is structurally sound.
     * If it is a 12-digit (UPC-A) or 13-digit (EAN-13) number, verifies the GS1 Modulo-10 checksum.
     */
    public static boolean isValidBarcode(String raw) {
        String clean = cleanCode(raw);
        if (clean.length() < 4 || clean.length() > 40) {
            return false;
        }

        // Pure digits: check GS1 Modulo-10 checksum for standard lengths
        if (clean.matches("^\\d+$")) {
            if (clean.length() == 12) {
                return validateUpcACheckDigit(clean);
            } else if (clean.length() == 13) {
                return validateEan13CheckDigit(clean);
            } else if (clean.length() == 8) {
                return validateEan8CheckDigit(clean);
            }
            return true;
        }

        return clean.matches("^[A-Za-z0-9]+$");
    }

    /**
     * GS1 Modulo-10 Check Digit for UPC-A (12 digits).
     */
    public static boolean validateUpcACheckDigit(String upc) {
        if (upc == null || upc.length() != 12 || !upc.matches("^\\d{12}$")) {
            return false;
        }
        int sumOdd = 0;
        int sumEven = 0;
        for (int i = 0; i < 11; i++) {
            int digit = upc.charAt(i) - '0';
            if (i % 2 == 0) {
                sumOdd += digit;
            } else {
                sumEven += digit;
            }
        }
        int total = (sumOdd * 3) + sumEven;
        int checkDigit = (10 - (total % 10)) % 10;
        int actualCheckDigit = upc.charAt(11) - '0';
        return checkDigit == actualCheckDigit;
    }

    /**
     * GS1 Modulo-10 Check Digit for EAN-13 (13 digits).
     */
    public static boolean validateEan13CheckDigit(String ean) {
        if (ean == null || ean.length() != 13 || !ean.matches("^\\d{13}$")) {
            return false;
        }
        int sumEven = 0;
        int sumOdd = 0;
        for (int i = 0; i < 12; i++) {
            int digit = ean.charAt(i) - '0';
            if (i % 2 == 0) {
                sumEven += digit;
            } else {
                sumOdd += digit;
            }
        }
        int total = sumEven + (sumOdd * 3);
        int checkDigit = (10 - (total % 10)) % 10;
        int actualCheckDigit = ean.charAt(12) - '0';
        return checkDigit == actualCheckDigit;
    }

    /**
     * GS1 Modulo-10 Check Digit for EAN-8 (8 digits).
     */
    public static boolean validateEan8CheckDigit(String ean8) {
        if (ean8 == null || ean8.length() != 8 || !ean8.matches("^\\d{8}$")) {
            return false;
        }
        int sumOdd = 0;
        int sumEven = 0;
        for (int i = 0; i < 7; i++) {
            int digit = ean8.charAt(i) - '0';
            if (i % 2 == 0) {
                sumOdd += digit;
            } else {
                sumEven += digit;
            }
        }
        int total = (sumOdd * 3) + sumEven;
        int checkDigit = (10 - (total % 10)) % 10;
        int actualCheckDigit = ean8.charAt(7) - '0';
        return checkDigit == actualCheckDigit;
    }
}
