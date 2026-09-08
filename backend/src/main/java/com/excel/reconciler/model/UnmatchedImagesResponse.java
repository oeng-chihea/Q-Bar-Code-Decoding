package com.excel.reconciler.model;

import java.util.List;

public class UnmatchedImagesResponse {
    private List<UnmatchedImageDownload> images;

    public UnmatchedImagesResponse() {
    }

    public UnmatchedImagesResponse(List<UnmatchedImageDownload> images) {
        this.images = images;
    }

    public List<UnmatchedImageDownload> getImages() {
        return images;
    }

    public void setImages(List<UnmatchedImageDownload> images) {
        this.images = images;
    }
}
