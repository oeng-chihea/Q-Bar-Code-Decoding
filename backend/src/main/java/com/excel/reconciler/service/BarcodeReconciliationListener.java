package com.excel.reconciler.service;

import com.excel.reconciler.config.RabbitMqConfig;
import com.excel.reconciler.model.BarcodeReconciliationRequest;
import com.excel.reconciler.model.ReconciliationResponse;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@Service
public class BarcodeReconciliationListener {
    private final BarcodeReconciliationRegistry registry;
    private final LocalReconciliationFileStorageService fileStorage;
    private final ReconciliationService reconciliationService;

    public BarcodeReconciliationListener(BarcodeReconciliationRegistry registry,
                                         LocalReconciliationFileStorageService fileStorage,
                                         ReconciliationService reconciliationService) {
        this.registry = registry;
        this.fileStorage = fileStorage;
        this.reconciliationService = reconciliationService;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    public void consume(BarcodeReconciliationRequest request) throws Exception {
        String reconciliationId = request.getReconciliationId();
        try {
            registry.markProcessing(reconciliationId);
            registry.markStage(reconciliationId, "Loading uploaded files");

            Path excelPath = fileStorage.resolveStoredPath(request.getExcelFilePath());
            MultipartFile excelFile = fileStorage.asMultipartFile(excelPath, "excelFile");
            List<Path> imagePaths = request.getImageFilePaths().stream()
                    .map(fileStorage::resolveStoredPath)
                    .toList();
            registry.setImagePaths(reconciliationId, imagePaths);
            List<MultipartFile> imageFiles = imagePaths.stream()
                    .map(path -> toMultipartFile(path.toString(), "images"))
                    .toList();

            registry.markStage(reconciliationId, "Running reconciliation pipeline");
            ReconciliationResponse response = reconciliationService.reconcile(
                    excelFile,
                    imageFiles,
                    request.getColumnName(),
                    request.isHighlightFullRow()
            );

            registry.markStage(reconciliationId, "Saving highlighted workbook");
            Path resultPath = fileStorage.saveResult(reconciliationId, response);
            registry.complete(reconciliationId, response, resultPath);
        } catch (Exception e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            try {
                registry.fail(reconciliationId, message);
            } catch (Exception stateException) {
                e.addSuppressed(stateException);
            }
            throw e;
        }
    }

    private MultipartFile toMultipartFile(String path, String partName) {
        try {
            return fileStorage.asMultipartFile(fileStorage.resolveStoredPath(path), partName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to load stored reconciliation image", e);
        }
    }
}
