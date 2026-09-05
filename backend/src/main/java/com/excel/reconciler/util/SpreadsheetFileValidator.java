package com.excel.reconciler.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SpreadsheetFileValidator {
    public static final String ERROR_MESSAGE =
            "Only images of an Excel table (.png, .jpg, .jpeg, .webp) are supported in the Excel upload.";

    private static final Pattern TABLE_IMAGE_FILENAME =
            Pattern.compile(".+\\.(png|jpg|jpeg|webp)$", Pattern.CASE_INSENSITIVE);

    private SpreadsheetFileValidator() {
    }

    public static boolean isSupported(MultipartFile file) {
        return isImage(file);
    }

    public static boolean isImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        String filename = file.getOriginalFilename();
        return filename != null && TABLE_IMAGE_FILENAME.matcher(filename).matches();
    }

    public static void requireSupported(MultipartFile file) {
        if (!isSupported(file)) {
            throw new IllegalArgumentException(ERROR_MESSAGE);
        }
    }
}
