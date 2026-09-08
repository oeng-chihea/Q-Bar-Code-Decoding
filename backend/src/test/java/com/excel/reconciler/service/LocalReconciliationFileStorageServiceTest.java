package com.excel.reconciler.service;

import com.excel.reconciler.model.ReconciliationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalReconciliationFileStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesExcelImagesAndResultInReconciliationDirectory() throws Exception {
        LocalReconciliationFileStorageService storage =
                new LocalReconciliationFileStorageService(temporaryDirectory.toString());
        MockMultipartFile excel = new MockMultipartFile(
                "excelFile", "inventory.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});
        MockMultipartFile image = new MockMultipartFile(
                "images", "barcode.png", "image/png", new byte[]{4, 5, 6});
        ReconciliationResponse response = new ReconciliationResponse();
        response.setHighlightedExcelBase64(Base64.getEncoder().encodeToString(new byte[]{7, 8, 9}));
        response.setDownloadFileName("inventory_highlighted.xlsx");

        Path excelPath = storage.saveExcelFile("rec-1", excel);
        List<Path> imagePaths = storage.saveImageFiles("rec-1", List.of(image));
        Path resultPath = storage.saveResult("rec-1", response);

        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(excelPath));
        assertArrayEquals(new byte[]{4, 5, 6}, Files.readAllBytes(imagePaths.get(0)));
        assertArrayEquals(new byte[]{7, 8, 9}, Files.readAllBytes(resultPath));
        assertEquals("inventory_highlighted.xlsx", response.getDownloadFileName());
    }

    @Test
    void rejectsPathTraversalInReconciliationId() {
        LocalReconciliationFileStorageService storage =
                new LocalReconciliationFileStorageService(temporaryDirectory.toString());

        assertThrows(IllegalArgumentException.class, () -> storage.directoryFor("../outside"));
    }

    @Test
    void createsUnmatchedImagesZipAndHandlesDuplicateFilenames() throws Exception {
        LocalReconciliationFileStorageService storage =
                new LocalReconciliationFileStorageService(temporaryDirectory.toString());

        Path image1 = temporaryDirectory.resolve("img1.png");
        Path image2 = temporaryDirectory.resolve("img2.png");
        Files.write(image1, new byte[]{10, 20});
        Files.write(image2, new byte[]{30, 40});

        Path zipPath = storage.createUnmatchedImagesZip(
                "rec-2",
                List.of(image1, image2),
                List.of("barcode.png", "barcode.png")
        );

        org.junit.jupiter.api.Assertions.assertTrue(Files.isRegularFile(zipPath));

        java.util.List<String> entryNames = new java.util.ArrayList<>();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipPath))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                zis.closeEntry();
            }
        }

        assertEquals(2, entryNames.size());
        assertEquals("barcode.png", entryNames.get(0));
        assertEquals("barcode (1).png", entryNames.get(1));
    }
}
