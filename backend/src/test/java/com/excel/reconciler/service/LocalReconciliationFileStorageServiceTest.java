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
    void listsStoredImagesInUploadOrderAndReportsTheirContentType() throws Exception {
        LocalReconciliationFileStorageService storage =
                new LocalReconciliationFileStorageService(temporaryDirectory.toString());

        MockMultipartFile firstImage = new MockMultipartFile(
                "images", "first.png", "image/png", new byte[]{10, 20});
        MockMultipartFile secondImage = new MockMultipartFile(
                "images", "second.jpg", "image/jpeg", new byte[]{30, 40});

        List<Path> savedPaths = storage.saveImageFiles("rec-2", List.of(firstImage, secondImage));
        List<Path> storedPaths = storage.getStoredImageFiles("rec-2");

        assertEquals(savedPaths, storedPaths);
        assertEquals("image/png", storage.contentTypeForPath(storedPaths.get(0)));
        assertEquals("image/jpeg", storage.contentTypeForPath(storedPaths.get(1)));
        assertArrayEquals(new byte[]{10, 20}, Files.readAllBytes(storedPaths.get(0)));
        assertArrayEquals(new byte[]{30, 40}, Files.readAllBytes(storedPaths.get(1)));
    }

    @Test
    void keepsDuplicateUploadNamesInSeparateStoredFiles() throws Exception {
        LocalReconciliationFileStorageService storage =
                new LocalReconciliationFileStorageService(temporaryDirectory.toString());

        MockMultipartFile firstImage = new MockMultipartFile(
                "images", "duplicate.png", "image/png", new byte[]{1, 2});
        MockMultipartFile secondImage = new MockMultipartFile(
                "images", "duplicate.png", "image/png", new byte[]{3, 4});

        List<Path> savedPaths = storage.saveImageFiles("rec-duplicates", List.of(firstImage, secondImage));

        assertEquals(2, savedPaths.stream().distinct().count());
        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(savedPaths.get(0)));
        assertArrayEquals(new byte[]{3, 4}, Files.readAllBytes(savedPaths.get(1)));
    }

    @Test
    void asMultipartFileStripsStorageIndexPrefix() throws Exception {
        LocalReconciliationFileStorageService storage =
                new LocalReconciliationFileStorageService(temporaryDirectory.toString());

        MockMultipartFile image = new MockMultipartFile(
                "images", "3.jpg", "image/jpeg", new byte[]{1, 2, 3});

        List<Path> savedPaths = storage.saveImageFiles("rec-prefix", List.of(image));
        org.springframework.web.multipart.MultipartFile loaded = storage.asMultipartFile(savedPaths.get(0), "images");

        assertEquals("3.jpg", loaded.getOriginalFilename());
    }

    @Test
    void listsStoredImagesInCorrectNumericalOrderBeyondTenImages() throws Exception {
        LocalReconciliationFileStorageService storage =
                new LocalReconciliationFileStorageService(temporaryDirectory.toString());

        List<org.springframework.web.multipart.MultipartFile> images = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> (org.springframework.web.multipart.MultipartFile) new MockMultipartFile("images", "item-" + i + ".png", "image/png", new byte[]{(byte) i}))
                .toList();

        List<Path> savedPaths = storage.saveImageFiles("rec-many", images);
        List<Path> storedPaths = storage.getStoredImageFiles("rec-many");

        assertEquals(savedPaths, storedPaths);
        assertEquals(12, storedPaths.size());
        assertEquals("0-item-0.png", storedPaths.get(0).getFileName().toString());
        assertEquals("10-item-10.png", storedPaths.get(10).getFileName().toString());
        assertEquals("11-item-11.png", storedPaths.get(11).getFileName().toString());
    }
}
