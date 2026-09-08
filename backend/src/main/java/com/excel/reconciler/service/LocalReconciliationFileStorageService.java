package com.excel.reconciler.service;

import com.excel.reconciler.model.ReconciliationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class LocalReconciliationFileStorageService {
    private static final String RESULT_FILE_NAME = "result.xlsx";
    private static final String UNMATCHED_ZIP_FILE_NAME = "unmatched_images.zip";

    private final Path storageRoot;

    public LocalReconciliationFileStorageService(
            @Value("${app.reconciliation.storage-root:runtime/reconciliations}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public Path directoryFor(String reconciliationId) {
        validateReconciliationId(reconciliationId);
        return storageRoot.resolve(reconciliationId).normalize();
    }

    public Path saveExcelFile(String reconciliationId, MultipartFile file) throws IOException {
        Objects.requireNonNull(file, "Excel file is required");
        return saveMultipartFile(directoryFor(reconciliationId).resolve("excel"), file, "input.xlsx");
    }

    public List<Path> saveImageFiles(String reconciliationId, List<MultipartFile> files) throws IOException {
        if (files == null) {
            return List.of();
        }

        Path imageDirectory = directoryFor(reconciliationId).resolve("images");
        return IntStream.range(0, files.size())
                .mapToObj(index -> {
                    try {
                        MultipartFile file = Objects.requireNonNull(files.get(index), "Image file is required");
                        String fallbackName = "image-" + index + ".bin";
                        return saveMultipartFile(imageDirectory, file, index + "-" + fallbackName);
                    } catch (IOException e) {
                        throw new StorageRuntimeException(e);
                    }
                })
                .toList();
    }

    public List<Path> getStoredImageFiles(String reconciliationId) throws IOException {
        validateReconciliationId(reconciliationId);
        Path imageDirectory = directoryFor(reconciliationId).resolve("images");
        if (!Files.isDirectory(imageDirectory)) {
            return List.of();
        }
        try (var stream = Files.list(imageDirectory)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }

    public Path saveResult(String reconciliationId, ReconciliationResponse response) throws IOException {
        Objects.requireNonNull(response, "Reconciliation response is required");
        String encodedWorkbook = response.getHighlightedExcelBase64();
        if (encodedWorkbook == null || encodedWorkbook.isBlank()) {
            throw new IOException("Reconciliation response does not contain an Excel result");
        }

        final byte[] workbookBytes;
        try {
            workbookBytes = Base64.getDecoder().decode(encodedWorkbook);
        } catch (IllegalArgumentException e) {
            throw new IOException("Reconciliation response contains invalid Excel data", e);
        }

        Path resultPath = directoryFor(reconciliationId).resolve(RESULT_FILE_NAME);
        Files.createDirectories(resultPath.getParent());
        Files.write(resultPath, workbookBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return resultPath;
    }

    public Path createUnmatchedImagesZip(String reconciliationId,
                                         List<Path> unmatchedImagePaths,
                                         List<String> originalFilenames) throws IOException {
        validateReconciliationId(reconciliationId);
        Objects.requireNonNull(unmatchedImagePaths, "Unmatched image paths are required");

        Path zipPath = directoryFor(reconciliationId).resolve(UNMATCHED_ZIP_FILE_NAME);
        Files.createDirectories(zipPath.getParent());

        Set<String> usedEntryNames = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (int i = 0; i < unmatchedImagePaths.size(); i++) {
                Path imagePath = unmatchedImagePaths.get(i);
                if (imagePath == null || !Files.isRegularFile(imagePath)) {
                    continue;
                }

                String filename = (originalFilenames != null && i < originalFilenames.size() && originalFilenames.get(i) != null && !originalFilenames.get(i).isBlank())
                        ? originalFilenames.get(i)
                        : imagePath.getFileName().toString();

                String entryName = deduplicateZipEntryName(filename, usedEntryNames);
                usedEntryNames.add(entryName);

                ZipEntry zipEntry = new ZipEntry(entryName);
                zos.putNextEntry(zipEntry);
                Files.copy(imagePath, zos);
                zos.closeEntry();
            }
        }

        return zipPath;
    }

    private String deduplicateZipEntryName(String originalName, Set<String> usedNames) {
        String baseName = originalName == null || originalName.isBlank()
                ? "image.bin"
                : Path.of(originalName).getFileName().toString();
        baseName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (baseName.isBlank() || baseName.equals(".") || baseName.equals("..")) {
            baseName = "image.bin";
        }

        if (!usedNames.contains(baseName)) {
            return baseName;
        }

        int dotIndex = baseName.lastIndexOf('.');
        String prefix = dotIndex > 0 ? baseName.substring(0, dotIndex) : baseName;
        String extension = dotIndex > 0 ? baseName.substring(dotIndex) : "";

        int count = 1;
        while (true) {
            String candidate = prefix + " (" + count + ")" + extension;
            if (!usedNames.contains(candidate)) {
                return candidate;
            }
            count++;
        }
    }

    public Path resolveStoredPath(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            throw new IllegalArgumentException("Stored file path is required");
        }

        Path candidate = Path.of(pathValue).toAbsolutePath().normalize();
        if (!candidate.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Stored file path is outside reconciliation storage");
        }
        return candidate;
    }

    public MultipartFile asMultipartFile(Path path, String partName) throws IOException {
        Path safePath = resolveStoredPath(path.toString());
        if (!Files.isRegularFile(safePath)) {
            throw new IOException("Stored file does not exist: " + safePath);
        }

        String filename = safePath.getFileName().toString();
        String contentType = Files.probeContentType(safePath);
        if (contentType == null) {
            contentType = contentTypeFor(filename);
        }
        return new StoredMultipartFile(partName, filename, contentType, safePath);
    }

    private Path saveMultipartFile(Path directory, MultipartFile file, String fallbackName) throws IOException {
        Files.createDirectories(directory);
        String filename = safeFilename(file.getOriginalFilename(), fallbackName);
        Path destination = directory.resolve(filename).normalize();
        if (!destination.startsWith(directory.toAbsolutePath().normalize())) {
            throw new IOException("Invalid file name");
        }
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return destination.toAbsolutePath().normalize();
    }

    private String safeFilename(String originalFilename, String fallbackName) {
        String filename = originalFilename == null || originalFilename.isBlank()
                ? fallbackName
                : Path.of(originalFilename).getFileName().toString();
        if (filename.equals(".") || filename.equals("..") || filename.isBlank()) {
            filename = fallbackName;
        }
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String contentTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }

    private void validateReconciliationId(String reconciliationId) {
        if (reconciliationId == null || !reconciliationId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,100}")) {
            throw new IllegalArgumentException("Invalid reconciliation ID");
        }
    }

    private static final class StorageRuntimeException extends RuntimeException {
        private StorageRuntimeException(IOException cause) {
            super(cause);
        }
    }
}
