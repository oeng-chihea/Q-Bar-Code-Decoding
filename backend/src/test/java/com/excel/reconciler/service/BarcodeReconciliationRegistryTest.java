package com.excel.reconciler.service;

import com.excel.reconciler.model.ReconciliationResponse;
import com.excel.reconciler.model.ReconciliationStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BarcodeReconciliationRegistryTest {

    @Test
    void createsQueuedReconciliation() {
        BarcodeReconciliationRegistry registry = new BarcodeReconciliationRegistry();

        BarcodeReconciliationRegistry.Record record = registry.create("rec-1");

        assertEquals("rec-1", record.reconciliationId());
        assertEquals(ReconciliationStatus.QUEUED, record.status());
    }

    @Test
    void storesCompletedResultAndFile() {
        BarcodeReconciliationRegistry registry = new BarcodeReconciliationRegistry();
        registry.create("rec-1");
        ReconciliationResponse response = new ReconciliationResponse();
        Path resultPath = Path.of("runtime/reconciliations/rec-1/result.xlsx");

        registry.complete("rec-1", response, resultPath);

        BarcodeReconciliationRegistry.Record record = registry.require("rec-1");
        assertEquals(ReconciliationStatus.COMPLETED, record.status());
        assertEquals(response, record.result());
        assertEquals(resultPath, record.resultPath());
    }

    @Test
    void storesFailureMessage() {
        BarcodeReconciliationRegistry registry = new BarcodeReconciliationRegistry();
        registry.create("rec-1");

        registry.fail("rec-1", "Gemini API unavailable");

        BarcodeReconciliationRegistry.Record record = registry.require("rec-1");
        assertEquals(ReconciliationStatus.FAILED, record.status());
        assertEquals("Gemini API unavailable", record.errorMessage());
    }

    @Test
    void rejectsUnknownReconciliationId() {
        BarcodeReconciliationRegistry registry = new BarcodeReconciliationRegistry();

        assertThrows(IllegalArgumentException.class, () -> registry.require("missing"));
    }
}
