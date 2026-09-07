package com.excel.reconciler.service;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class StoredMultipartFile implements MultipartFile {
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final Path path;

    StoredMultipartFile(String name, String originalFilename, String contentType, Path path) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.path = path;
    }

    @Override
    @NonNull
    public String getName() {
        return name != null ? name : "";
    }

    @Override
    @Nullable
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    @Nullable
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return getSize() == 0;
    }

    @Override
    public long getSize() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    @NonNull
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    @NonNull
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(@NonNull File destination) throws IOException {
        Files.copy(path, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void transferTo(@NonNull Path destination) throws IOException {
        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}
