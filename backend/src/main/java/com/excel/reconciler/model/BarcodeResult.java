package com.excel.reconciler.model;

import java.util.List;

public class BarcodeResult {
    private String filename;
    private String decodedValue;
    private List<String> allExtractedValues;
    private String decoderType; // "ZXING", "OLLAMA_AI", "FAILED"
    private boolean success;
    private String barcodeFormat;
    private String errorMessage;

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
}
