package com.excel.reconciler.service;

import java.text.Normalizer;

/**
 * Produces a comparison-only representation of an identifier.
 *
 * The original cell value must always be retained separately. In particular,
 * this class must never be used to rewrite values shown to a user or written
 * back to an Excel workbook.
 */
public final class IdentifierCanonicalizer {

    private IdentifierCanonicalizer() {
    }

    public static String canonicalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replace('\u00A0', ' ');
        StringBuilder result = new StringBuilder(normalized.length());

        normalized.codePoints().forEach(codePoint -> {
            int mapped = mapKhmerDigit(codePoint);
            int type = Character.getType(mapped);

            // Format characters include zero-width spaces and BOMs. They can
            // appear in copied Khmer text, but are not part of an identifier.
            if (Character.isWhitespace(mapped) || type == Character.FORMAT
                    || isIdentifierSeparator(mapped)) {
                return;
            }

            result.appendCodePoint(Character.toLowerCase(mapped));
        });

        return result.toString();
    }

    private static int mapKhmerDigit(int codePoint) {
        if (codePoint >= '\u17E0' && codePoint <= '\u17E9') {
            return '0' + (codePoint - '\u17E0');
        }
        return codePoint;
    }

    private static boolean isIdentifierSeparator(int codePoint) {
        return switch (codePoint) {
            case '_', '-', '/', ':', '(', ')', '!', '\'' -> true;
            default -> false;
        };
    }
}
