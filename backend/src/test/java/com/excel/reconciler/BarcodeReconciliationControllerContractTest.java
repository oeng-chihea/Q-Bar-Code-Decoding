package com.excel.reconciler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.excel.reconciler.controller.BarcodeReconciliationController;
import com.excel.reconciler.model.BarcodeReconciliationRequest;
import com.excel.reconciler.model.BarcodeResult;
import com.excel.reconciler.model.ReconciliationResponse;
import com.excel.reconciler.service.BarcodeReconciliationPublisher;
import com.excel.reconciler.service.BarcodeReconciliationRegistry;
import com.excel.reconciler.service.LocalReconciliationFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BarcodeReconciliationControllerContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BarcodeReconciliationRegistry registry = new BarcodeReconciliationRegistry();
    private CapturingRabbitTemplate rabbitTemplate;
    private LocalReconciliationFileStorageService fileStorage;
    private MockMvc mockMvc;

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp() {
        rabbitTemplate = new CapturingRabbitTemplate();
        fileStorage = new LocalReconciliationFileStorageService(temporaryDirectory.toString());
        BarcodeReconciliationPublisher publisher = new BarcodeReconciliationPublisher(rabbitTemplate);
        BarcodeReconciliationController controller = new BarcodeReconciliationController(
                publisher,
                registry,
                fileStorage
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void acceptsSubmissionAndReturnsReconciliationId() throws Exception {
        var result = mockMvc.perform(multipart("/api/v1/barcodes/reconcile")
                        .file(new MockMultipartFile("excelFile", "inventory.png", "image/png", new byte[]{1, 2, 3}))
                        .file(new MockMultipartFile("images", "barcode.png", "image/png", new byte[]{4, 5, 6})))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reconciliationId").isString())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String reconciliationId = body.get("reconciliationId").asText();
        assertEquals("/api/v1/barcode-reconciliations/" + reconciliationId, body.get("statusUrl").asText());

        BarcodeReconciliationRequest request = assertInstanceOf(
                BarcodeReconciliationRequest.class,
                rabbitTemplate.message
        );
        assertEquals(reconciliationId, request.getReconciliationId());
        assertEquals(1, request.getImageFilePaths().size());
        assertEquals("QUEUED", registry.require(reconciliationId).status().name());
    }

    @Test
    void exposesCompletedResultAndDownload() throws Exception {
        String reconciliationId = "rec-1";
        registry.create(reconciliationId);

        ReconciliationResponse response = new ReconciliationResponse();
        response.setMatchedRowsCount(1);
        response.setDownloadFileName("inventory_highlighted.xlsx");
        response.setHighlightedExcelBase64(Base64.getEncoder().encodeToString(new byte[]{7, 8, 9}));
        Path resultPath = fileStorage.saveResult(reconciliationId, response);
        registry.complete(reconciliationId, response, resultPath);

        mockMvc.perform(get("/api/v1/barcode-reconciliations/{id}", reconciliationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.resultAvailable").value(true))
                .andExpect(jsonPath("$.downloadFileName").value("inventory_highlighted.xlsx"));

        mockMvc.perform(get("/api/v1/barcode-reconciliations/{id}/result", reconciliationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedRowsCount").value(1));

        mockMvc.perform(get("/api/v1/barcode-reconciliations/{id}/download", reconciliationId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{7, 8, 9}))
                .andExpect(header().string("Content-Disposition", containsString("inventory_highlighted.xlsx")));
    }

    @Test
    void reportsNotReadyAndUnknownReconciliations() throws Exception {
        registry.create("queued-1");

        mockMvc.perform(get("/api/v1/barcode-reconciliations/queued-1/result"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(get("/api/v1/barcode-reconciliations/queued-1/download-unmatched"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(get("/api/v1/barcode-reconciliations/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void handlesDownloadUnmatchedImagesEndpoint() throws Exception {
        String reconciliationId = "rec-unmatched";
        registry.create(reconciliationId);

        // Save image to storage
        MockMultipartFile image1 = new MockMultipartFile("images", "item1.png", "image/png", new byte[]{1, 2, 3});
        MockMultipartFile image2 = new MockMultipartFile("images", "item2.png", "image/png", new byte[]{4, 5, 6});
        List<Path> paths = fileStorage.saveImageFiles(reconciliationId, List.of(image1, image2));
        registry.setImagePaths(reconciliationId, paths);

        BarcodeResult res1 = new BarcodeResult("item1.png", "CODE1", List.of("CODE1"), "ZXING", true, "QR_CODE", null);
        res1.setMatched(true);
        BarcodeResult res2 = new BarcodeResult("item2.png", "CODE2", List.of("CODE2"), "ZXING", true, "QR_CODE", null);
        res2.setMatched(false);

        ReconciliationResponse response = new ReconciliationResponse();
        response.setScanResults(List.of(res1, res2));
        response.setDownloadFileName("my_catalog_highlighted.xlsx");
        response.setHighlightedExcelBase64(Base64.getEncoder().encodeToString(new byte[]{7, 8}));
        Path resultPath = fileStorage.saveResult(reconciliationId, response);
        registry.complete(reconciliationId, response, resultPath);

        mockMvc.perform(get("/api/v1/barcode-reconciliations/{id}/download-unmatched", reconciliationId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("my_catalog_unmatched_images.zip")));
    }

    @Test
    void returnsBadRequestWhenNoUnmatchedImagesExist() throws Exception {
        String reconciliationId = "rec-all-matched";
        registry.create(reconciliationId);

        BarcodeResult res1 = new BarcodeResult("item1.png", "CODE1", List.of("CODE1"), "ZXING", true, "QR_CODE", null);
        res1.setMatched(true);

        ReconciliationResponse response = new ReconciliationResponse();
        response.setScanResults(List.of(res1));
        response.setDownloadFileName("catalog_highlighted.xlsx");
        response.setHighlightedExcelBase64(Base64.getEncoder().encodeToString(new byte[]{7, 8}));
        Path resultPath = fileStorage.saveResult(reconciliationId, response);
        registry.complete(reconciliationId, response, resultPath);

        mockMvc.perform(get("/api/v1/barcode-reconciliations/{id}/download-unmatched", reconciliationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No unmatched images found"));
    }

    private static final class CapturingRabbitTemplate extends RabbitTemplate {
        private Object message;

        @Override
        public void convertAndSend(String exchange, String routingKey, Object object) {
            message = object;
        }
    }
}

