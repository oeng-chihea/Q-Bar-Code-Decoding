package com.excel.reconciler.model;

import java.util.List;

public class BarcodeResult {
    private String filename;
    private String decodedValue;
    private List<String> allExtractedValues;
    private String decoderType; // "ZXING", "GEMINI_AI", "FAILED"
    private boolean success;
    private String barcodeFormat;
    private String errorMessage;
    private Boolean matched;
    private boolean duplicate; // another photo already matched the same Excel row
    private String createDate; // ISO date (yyyy-MM-dd) read from the receipt's CREATED line
    private String createDateText; // the CREATED text as read, for messages
    private String createDateProblem; // NOT_FOUND, UNREADABLE or FUTURE_DATE when createDate is unusable

    public BarcodeResult() {
    }

    public BarcodeResult(String filename, String decodedValue, List<String> allExtractedValues,
                         String decoderType, boolean success, String barcodeFormat, String errorMessage) {
        this.filename = filename;
        this.decodedValue = decodedValue;
        this.allExtractedValues = allExtractedValues;
        this.decoderType = decoderType;
        this.success = success;
        this.barcodeFormat = barcodeFormat;
        this.errorMessage = errorMessage;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getDecodedValue() {
        return decodedValue;
    }

    public void setDecodedValue(String decodedValue) {
        this.decodedValue = decodedValue;
    }

    public List<String> getAllExtractedValues() {
        return allExtractedValues;
    }

    public void setAllExtractedValues(List<String> allExtractedValues) {
        this.allExtractedValues = allExtractedValues;
    }

    public String getDecoderType() {
        return decoderType;
    }

    public void setDecoderType(String decoderType) {
        this.decoderType = decoderType;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getBarcodeFormat() {
        return barcodeFormat;
    }

    public void setBarcodeFormat(String barcodeFormat) {
        this.barcodeFormat = barcodeFormat;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Boolean isMatched() {
        return matched;
    }

    public void setMatched(Boolean matched) {
        this.matched = matched;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public String getCreateDate() {
        return createDate;
    }

    public void setCreateDate(String createDate) {
        this.createDate = createDate;
    }

    public String getCreateDateText() {
        return createDateText;
    }

    public void setCreateDateText(String createDateText) {
        this.createDateText = createDateText;
    }

    public String getCreateDateProblem() {
        return createDateProblem;
    }

    public void setCreateDateProblem(String createDateProblem) {
        this.createDateProblem = createDateProblem;
    }
}
