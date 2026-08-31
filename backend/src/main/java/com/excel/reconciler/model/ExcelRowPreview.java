package com.excel.reconciler.model;

import java.util.Map;

public class ExcelRowPreview {
    private int rowIndex;
    private Map<String, String> cells;
    private String barcodeValue;
    private boolean matched;

    public ExcelRowPreview() {
    }

    public ExcelRowPreview(int rowIndex, Map<String, String> cells, String barcodeValue, boolean matched) {
        this.rowIndex = rowIndex;
        this.cells = cells;
        this.barcodeValue = barcodeValue;
        this.matched = matched;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public Map<String, String> getCells() {
        return cells;
    }

    public void setCells(Map<String, String> cells) {
        this.cells = cells;
    }

    public String getBarcodeValue() {
        return barcodeValue;
    }

    public void setBarcodeValue(String barcodeValue) {
        this.barcodeValue = barcodeValue;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }
}
