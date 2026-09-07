package com.excel.reconciler.model;

public class BarcodeReconciliationSubmissionResponse {
    private String reconciliationId;
    private ReconciliationStatus status;
    private String statusUrl;

    public BarcodeReconciliationSubmissionResponse() {
    }

    public BarcodeReconciliationSubmissionResponse(String reconciliationId,
                                                    ReconciliationStatus status,
                                                    String statusUrl) {
        this.reconciliationId = reconciliationId;
        this.status = status;
        this.statusUrl = statusUrl;
    }

    public String getReconciliationId() {
        return reconciliationId;
    }

    public void setReconciliationId(String reconciliationId) {
        this.reconciliationId = reconciliationId;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationStatus status) {
        this.status = status;
    }

    public String getStatusUrl() {
        return statusUrl;
    }

    public void setStatusUrl(String statusUrl) {
        this.statusUrl = statusUrl;
    }
}
