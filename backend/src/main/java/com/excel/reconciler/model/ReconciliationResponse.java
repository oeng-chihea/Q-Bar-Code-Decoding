package com.excel.reconciler.model;

import java.util.List;
import java.util.Set;

public class ReconciliationResponse {
    private int totalImages;
    private int decodedImagesCount;
    private int excelTotalRows;
    private int matchedRowsCount;
    private int unmatchedImagesCount;
    private String matchedColumnName;
    private double matchedColumnConfidence;
    private List<Integer> identifierColumnIndexes = List.of();
    private String activeSheetName;
    private List<String> columns;
    private List<BarcodeResult> scanResults;
    private Set<String> allDecodedCodes;
    private Set<String> matchedCodes;
    private Set<String> unmatchedCodes;
    private List<ExcelRowPreview> previewRows;
    private String highlightedExcelBase64;
    private String downloadFileName;
    private String excelSourceType;
    private long executionTimeMs;

    public ReconciliationResponse() {
    }

    public int getTotalImages() {
        return totalImages;
    }

    public void setTotalImages(int totalImages) {
        this.totalImages = totalImages;
    }

    public int getDecodedImagesCount() {
        return decodedImagesCount;
    }

    public void setDecodedImagesCount(int decodedImagesCount) {
        this.decodedImagesCount = decodedImagesCount;
    }

    public int getExcelTotalRows() {
        return excelTotalRows;
    }

    public void setExcelTotalRows(int excelTotalRows) {
        this.excelTotalRows = excelTotalRows;
    }

    public int getMatchedRowsCount() {
        return matchedRowsCount;
    }

    public void setMatchedRowsCount(int matchedRowsCount) {
        this.matchedRowsCount = matchedRowsCount;
    }

    public int getUnmatchedImagesCount() {
        return unmatchedImagesCount;
    }

    public void setUnmatchedImagesCount(int unmatchedImagesCount) {
        this.unmatchedImagesCount = unmatchedImagesCount;
    }

    public String getMatchedColumnName() {
        return matchedColumnName;
    }

    public void setMatchedColumnName(String matchedColumnName) {
        this.matchedColumnName = matchedColumnName;
    }

    public double getMatchedColumnConfidence() {
        return matchedColumnConfidence;
    }

    public void setMatchedColumnConfidence(double matchedColumnConfidence) {
        this.matchedColumnConfidence = matchedColumnConfidence;
    }

    public List<Integer> getIdentifierColumnIndexes() {
        return identifierColumnIndexes;
    }

    public void setIdentifierColumnIndexes(List<Integer> identifierColumnIndexes) {
        this.identifierColumnIndexes = identifierColumnIndexes;
    }

    public String getActiveSheetName() {
        return activeSheetName;
    }

    public void setActiveSheetName(String activeSheetName) {
        this.activeSheetName = activeSheetName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<BarcodeResult> getScanResults() {
        return scanResults;
    }

    public void setScanResults(List<BarcodeResult> scanResults) {
        this.scanResults = scanResults;
    }

    public Set<String> getAllDecodedCodes() {
        return allDecodedCodes;
    }

    public void setAllDecodedCodes(Set<String> allDecodedCodes) {
        this.allDecodedCodes = allDecodedCodes;
    }

    public Set<String> getMatchedCodes() {
        return matchedCodes;
    }

    public void setMatchedCodes(Set<String> matchedCodes) {
        this.matchedCodes = matchedCodes;
    }

    public Set<String> getUnmatchedCodes() {
        return unmatchedCodes;
    }

    public void setUnmatchedCodes(Set<String> unmatchedCodes) {
        this.unmatchedCodes = unmatchedCodes;
    }

    public List<ExcelRowPreview> getPreviewRows() {
        return previewRows;
    }

    public void setPreviewRows(List<ExcelRowPreview> previewRows) {
        this.previewRows = previewRows;
    }

    public String getHighlightedExcelBase64() {
        return highlightedExcelBase64;
    }

    public void setHighlightedExcelBase64(String highlightedExcelBase64) {
        this.highlightedExcelBase64 = highlightedExcelBase64;
    }

    public String getDownloadFileName() {
        return downloadFileName;
    }

    public void setDownloadFileName(String downloadFileName) {
        this.downloadFileName = downloadFileName;
    }

    public String getExcelSourceType() {
        return excelSourceType;
    }

    public void setExcelSourceType(String excelSourceType) {
        this.excelSourceType = excelSourceType;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }
}
