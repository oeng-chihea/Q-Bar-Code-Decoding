package com.excel.reconciler.service;

import com.excel.reconciler.model.ReconciliationResponse;
import com.excel.reconciler.model.ReconciliationStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class BarcodeReconciliationRegistry {
    private final ConcurrentMap<String, Record> records = new ConcurrentHashMap<>();

    public Record create(String reconciliationId) {
        if (reconciliationId == null || reconciliationId.isBlank()) {
            throw new IllegalArgumentException("Reconciliation ID is required");
        }
        Record record = new Record(reconciliationId);
        if (records.putIfAbsent(reconciliationId, record) != null) {
            throw new IllegalArgumentException("Reconciliation ID already exists: " + reconciliationId);
        }
        return record;
    }

    public Record require(String reconciliationId) {
        Record record = records.get(reconciliationId);
        if (record == null) {
            throw new IllegalArgumentException("Unknown reconciliation ID: " + reconciliationId);
        }
        return record;
    }

    public void markProcessing(String reconciliationId) {
        Record record = require(reconciliationId);
        record.status = ReconciliationStatus.PROCESSING;
        record.stage = "Processing reconciliation";
        record.errorMessage = null;
    }

    public void markStage(String reconciliationId, String stage) {
        require(reconciliationId).stage = stage;
    }

    public void complete(String reconciliationId, ReconciliationResponse result, Path resultPath) {
        Record record = require(reconciliationId);
        record.result = result;
        record.resultPath = resultPath;
        record.downloadFileName = result != null ? result.getDownloadFileName() : null;
        record.stage = "Completed";
        record.errorMessage = null;
        record.status = ReconciliationStatus.COMPLETED;
    }

    public void fail(String reconciliationId, String errorMessage) {
        Record record = require(reconciliationId);
        record.status = ReconciliationStatus.FAILED;
        record.stage = "Failed";
        record.errorMessage = errorMessage;
    }

    public static final class Record {
        private final String reconciliationId;
        private volatile ReconciliationStatus status;
        private volatile String stage;
        private volatile String errorMessage;
        private volatile ReconciliationResponse result;
        private volatile Path resultPath;
        private volatile String downloadFileName;

        private Record(String reconciliationId) {
            this.reconciliationId = reconciliationId;
            this.status = ReconciliationStatus.QUEUED;
            this.stage = "Queued";
        }

        public String reconciliationId() {
            return reconciliationId;
        }

        public ReconciliationStatus status() {
            return status;
        }

        public String stage() {
            return stage;
        }

        public String errorMessage() {
            return errorMessage;
        }

        public ReconciliationResponse result() {
            return result;
        }

        public Path resultPath() {
            return resultPath;
        }

        public String downloadFileName() {
            return downloadFileName;
        }
    }
}
