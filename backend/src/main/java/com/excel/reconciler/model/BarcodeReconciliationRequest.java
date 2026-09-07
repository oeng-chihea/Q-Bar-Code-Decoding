package com.excel.reconciler.model;

import java.util.ArrayList;
import java.util.List;

public class BarcodeReconciliationRequest {
    private String reconciliationId;
    private String excelFilePath;
    private List<String> imageFilePaths = new ArrayList<>();
    private String columnName;
    private boolean highlightFullRow;

    public BarcodeReconciliationRequest() {
    }

    public BarcodeReconciliationRequest(String reconciliationId,
                                        String excelFilePath,
                                        List<String> imageFilePaths,
                                        String columnName,
                                        boolean highlightFullRow) {
        this.reconciliationId = reconciliationId;
        this.excelFilePath = excelFilePath;
        this.imageFilePaths = imageFilePaths != null ? new ArrayList<>(imageFilePaths) : new ArrayList<>();
        this.columnName = columnName;
        this.highlightFullRow = highlightFullRow;
    }

    public String getReconciliationId() {
        return reconciliationId;
    }

    public void setReconciliationId(String reconciliationId) {
        this.reconciliationId = reconciliationId;
    }

    public String getExcelFilePath() {
        return excelFilePath;
    }

    public void setExcelFilePath(String excelFilePath) {
        this.excelFilePath = excelFilePath;
    }

    public List<String> getImageFilePaths() {
        return imageFilePaths;
    }

    public void setImageFilePaths(List<String> imageFilePaths) {
        this.imageFilePaths = imageFilePaths != null ? new ArrayList<>(imageFilePaths) : new ArrayList<>();
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public boolean isHighlightFullRow() {
        return highlightFullRow;
    }

    public void setHighlightFullRow(boolean highlightFullRow) {
        this.highlightFullRow = highlightFullRow;
    }
}
