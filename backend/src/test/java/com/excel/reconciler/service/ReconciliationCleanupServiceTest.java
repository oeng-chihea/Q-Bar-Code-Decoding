package com.excel.reconciler.service;

import com.excel.reconciler.model.ReconciliationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationCleanupServiceTest {

    @TempDir
    Path storageRoot;

    private LocalReconciliationFileStorageService storage;
    private BarcodeReconciliationRegistry registry;
    private ReconciliationCleanupService cleanup;

    @BeforeEach
    void setUp() {
        storage = new LocalReconciliationFileStorageService(storageRoot.toString());
        registry = new BarcodeReconciliationRegistry();
        cleanup = new ReconciliationCleanupService(storage, registry, 24);
    }

    @Test
    void deletesExpiredFinishedReconciliationAndForgetsItsRecord() throws Exception {
        createCompletedReconciliation("old-rec");
        setAge("old-rec", Duration.ofHours(25));

        int deleted = cleanup.deleteOlderThan(Instant.now().minus(Duration.ofHours(24)));

        assertEquals(1, deleted);
        assertFalse(Files.exists(storageRoot.resolve("old-rec")));
        assertThrows(IllegalArgumentException.class, () -> registry.require("old-rec"));
    }

    @Test
    void keepsReconciliationsYoungerThanRetention() throws Exception {
        createCompletedReconciliation("recent-rec");
        setAge("recent-rec", Duration.ofHours(2));

        int deleted = cleanup.deleteOlderThan(Instant.now().minus(Duration.ofHours(24)));

        assertEquals(0, deleted);
        assertTrue(Files.exists(storageRoot.resolve("recent-rec")));
    }

    @Test
    void neverDeletesQueuedOrProcessingReconciliation() throws Exception {
        registry.create("queued-rec");
        storage.saveImageFiles("queued-rec", List.of(image()));
        registry.create("processing-rec");
        storage.saveImageFiles("processing-rec", List.of(image()));
        registry.markProcessing("processing-rec");
        setAge("queued-rec", Duration.ofHours(48));
        setAge("processing-rec", Duration.ofHours(48));

        int deleted = cleanup.deleteOlderThan(Instant.now().minus(Duration.ofHours(24)));

        assertEquals(0, deleted);
        assertTrue(Files.exists(storageRoot.resolve("queued-rec")));
        assertTrue(Files.exists(storageRoot.resolve("processing-rec")));
    }

    @Test
    void deletesOrphanedFoldersLeftFromBeforeARestart() throws Exception {
        // Folder on disk with no in-memory record, as happens after the server restarts
        storage.saveImageFiles("orphan-rec", List.of(image()));
        setAge("orphan-rec", Duration.ofDays(10));

        int deleted = cleanup.deleteOlderThan(Instant.now().minus(Duration.ofHours(24)));

        assertEquals(1, deleted);
        assertFalse(Files.exists(storageRoot.resolve("orphan-rec")));
    }

    @Test
    void zeroRetentionDisablesScheduledCleanup() throws Exception {
        storage.saveImageFiles("old-rec", List.of(image()));
        setAge("old-rec", Duration.ofDays(10));

        new ReconciliationCleanupService(storage, registry, 0).cleanupExpiredReconciliations();

        assertTrue(Files.exists(storageRoot.resolve("old-rec")));
    }

    private void createCompletedReconciliation(String reconciliationId) throws Exception {
        registry.create(reconciliationId);
        storage.saveImageFiles(reconciliationId, List.of(image()));
        ReconciliationResponse response = new ReconciliationResponse();
        response.setHighlightedExcelBase64("AQID");
        Path resultPath = storage.saveResult(reconciliationId, response);
        registry.complete(reconciliationId, response, resultPath);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile(
                "images", "barcode.png", "image/png", new byte[]{1, 2, 3});
    }

    private void setAge(String reconciliationId, Duration age) throws IOException {
        FileTime time = FileTime.from(Instant.now().minus(age));
        try (var paths = Files.walk(storageRoot.resolve(reconciliationId))) {
            for (Path path : paths.toList()) {
                Files.setLastModifiedTime(path, time);
            }
        }
    }
}
