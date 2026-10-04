package com.excel.reconciler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Deletes stored uploads and results of finished reconciliations once they are older than the
 * retention period, and forgets their in-memory records so no stale download links remain.
 */
@Service
public class ReconciliationCleanupService {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationCleanupService.class);

    private final LocalReconciliationFileStorageService fileStorage;
    private final BarcodeReconciliationRegistry registry;
    private final long retentionHours;

    public ReconciliationCleanupService(LocalReconciliationFileStorageService fileStorage,
                                        BarcodeReconciliationRegistry registry,
                                        @Value("${app.reconciliation.retention-hours:24}") long retentionHours) {
        this.fileStorage = fileStorage;
        this.registry = registry;
        this.retentionHours = retentionHours;
    }

    @Scheduled(initialDelayString = "${app.reconciliation.cleanup.initial-delay-ms:60000}",
               fixedDelayString = "${app.reconciliation.cleanup.interval-ms:3600000}")
    public void cleanupExpiredReconciliations() {
        if (retentionHours <= 0) {
            return;
        }
        int deleted = deleteOlderThan(Instant.now().minus(Duration.ofHours(retentionHours)));
        if (deleted > 0) {
            log.info("Deleted {} reconciliation folder(s) older than {}h", deleted, retentionHours);
        }
    }

    public int deleteOlderThan(Instant cutoff) {
        List<String> reconciliationIds;
        try {
            reconciliationIds = fileStorage.listStoredReconciliationIds();
        } catch (IOException e) {
            log.warn("Could not list reconciliation storage for cleanup: {}", e.getMessage());
            return 0;
        }

        int deleted = 0;
        for (String reconciliationId : reconciliationIds) {
            // Never touch a job that is still queued or being processed
            if (registry.isActive(reconciliationId)) {
                continue;
            }
            try {
                if (fileStorage.lastModified(reconciliationId).isBefore(cutoff)) {
                    registry.remove(reconciliationId);
                    fileStorage.deleteReconciliation(reconciliationId);
                    deleted++;
                }
            } catch (IOException e) {
                log.warn("Could not delete reconciliation {}: {}", reconciliationId, e.getMessage());
            }
        }
        return deleted;
    }
}
