package com.excel.reconciler.model;

public class UnmatchedImageDownload {
    private int imageIndex;
    private String filename;
    private String contentType;
    private long size;
    private String downloadUrl;

    public UnmatchedImageDownload() {
    }

    public UnmatchedImageDownload(int imageIndex,
                                  String filename,
                                  String contentType,
                                  long size,
                                  String downloadUrl) {
        this.imageIndex = imageIndex;
        this.filename = filename;
        this.contentType = contentType;
        this.size = size;
        this.downloadUrl = downloadUrl;
    }

    public int getImageIndex() {
        return imageIndex;
    }

    public void setImageIndex(int imageIndex) {
        this.imageIndex = imageIndex;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
