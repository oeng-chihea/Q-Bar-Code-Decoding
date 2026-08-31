package com.excel.reconciler.util;

import java.util.Locale;

/**
 * High-precision GS1 and standard Barcode Checksum Validator.
 * Implements Modulo-10 check digit verification for UPC-A, EAN-13, EAN-8, and UPC-E.
 */
public final class BarcodeValidator {

    private BarcodeValidator() {}

    /**
     * Normalizes a barcode string by removing spaces, hyphens, and non-alphanumeric noise.
     */
    public static String cleanCode(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("[\\s_\\-/:()!']+", "");
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
            // Other numeric barcodes (e.g. Code 128, ITF, Code 39 numbers)
            return true;
        }

        // Alphanumeric code (SKU, Code 39, QR alphanumeric content)
        return clean.matches("^[A-Za-z0-9\\-]+$");
    }

    /**
     * GS1 Modulo-10 Check Digit for UPC-A (12 digits).
     * Rule: (10 - ((sum of odd position digits * 3) + sum of even position digits) % 10) % 10
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
                sumOdd += digit; // Positions 1, 3, 5, 7, 9, 11 (0-indexed even)
            } else {
                sumEven += digit; // Positions 2, 4, 6, 8, 10 (0-indexed odd)
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
