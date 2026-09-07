package com.excel.reconciler.model;

public class BarcodeReconciliationStatusResponse {
    private String reconciliationId;
    private ReconciliationStatus status;
    private String stage;
    private String errorMessage;
    private boolean resultAvailable;
    private String downloadFileName;

    public BarcodeReconciliationStatusResponse() {
    }

    public BarcodeReconciliationStatusResponse(String reconciliationId,
                                               ReconciliationStatus status,
                                               String stage,
                                               String errorMessage,
                                               boolean resultAvailable,
                                               String downloadFileName) {
        this.reconciliationId = reconciliationId;
        this.status = status;
        this.stage = stage;
        this.errorMessage = errorMessage;
        this.resultAvailable = resultAvailable;
        this.downloadFileName = downloadFileName;
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

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isResultAvailable() {
        return resultAvailable;
    }

    public void setResultAvailable(boolean resultAvailable) {
        this.resultAvailable = resultAvailable;
    }

    public String getDownloadFileName() {
        return downloadFileName;
    }

    public void setDownloadFileName(String downloadFileName) {
        this.downloadFileName = downloadFileName;
    }
}
