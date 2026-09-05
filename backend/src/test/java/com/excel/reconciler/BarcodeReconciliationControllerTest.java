package com.excel.reconciler;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
public class BarcodeReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.excel.reconciler.service.OllamaVisionService ollamaVisionService;

    @Test
    public void testReconcileEndpoint() throws Exception {
        // 1. Create test Excel with a "QR Barcode" column
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Product Name");
        header.createCell(1).setCellValue("QR Barcode");

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("Smartphone");
        row1.createCell(1).setCellValue("SKU-9901");

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("Tablet");
        row2.createCell(1).setCellValue("SKU-9902");

        ByteArrayOutputStream excelOut = new ByteArrayOutputStream();
        workbook.write(excelOut);
        workbook.close();

        MockMultipartFile excelFile = new MockMultipartFile(
                "excelFile",
                "inventory.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelOut.toByteArray()
        );

        // 2. Create QR code image for SKU-9901
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode("SKU-9901", BarcodeFormat.QR_CODE, 200, 200);
        ByteArrayOutputStream imgOut = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", imgOut);

        MockMultipartFile imgFile = new MockMultipartFile(
                "images",
                "sku_9901.png",
                "image/png",
                imgOut.toByteArray()
        );

        mockMvc.perform(multipart("/api/v1/barcodes/reconcile")
                        .file(excelFile)
                        .file(imgFile)
                        .param("columnName", "QR Barcode")
                        .param("highlightFullRow", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalImages").value(1))
                .andExpect(jsonPath("$.decodedImagesCount").value(1))
                .andExpect(jsonPath("$.excelTotalRows").value(2))
                .andExpect(jsonPath("$.matchedRowsCount").value(1))
                .andExpect(jsonPath("$.matchedCodes[0]").value("SKU-9901"))
                .andExpect(jsonPath("$.highlightedExcelBase64").isNotEmpty());
    }

    @Test
    public void testReconcileExcelTableImage() throws Exception {
        when(ollamaVisionService.generateJson(
                any(byte[].class), eq("image/png"), anyString(), anyMap()))
                .thenReturn(new com.excel.reconciler.service.OllamaVisionService.JsonResponse(
                        200,
                        """
                        {
                          "isExcelTable": true,
                          "isBarcodeImage": false,
                          "sheetName": "Scanned Inventory",
                          "headers": ["Product", "Barcode"],
                          "rows": [{"values": ["Phone", "SKU-9901"]}]
                        }
                        """,
                        null));

        MockMultipartFile tableImage = new MockMultipartFile(
                "excelFile", "inventory.png", "image/png", new byte[]{9, 8, 7});
        MockMultipartFile barcodeImage = new MockMultipartFile(
                "images", "sku_9901.png", "image/png", createQrCode("SKU-9901"));

        mockMvc.perform(multipart("/api/v1/barcodes/reconcile")
                        .file(tableImage)
                        .file(barcodeImage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excelSourceType").value("EXCEL_TABLE_IMAGE"))
                .andExpect(jsonPath("$.excelTotalRows").value(1))
                .andExpect(jsonPath("$.matchedRowsCount").value(1))
                .andExpect(jsonPath("$.matchedCodes[0]").value("SKU-9901"));
    }

    @Test
    public void testRejectBarcodeImageUploadedAsExcelFile() throws Exception {
        // Create a QR code image and try to upload it as the excelFile
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode("BARCODE-777", BarcodeFormat.QR_CODE, 200, 200);
        ByteArrayOutputStream imgOut = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", imgOut);

        MockMultipartFile barcodeInExcelSlot = new MockMultipartFile(
                "excelFile",
                "barcode_sticker.png",
                "image/png",
                imgOut.toByteArray()
        );

        MockMultipartFile imgFile = new MockMultipartFile(
                "images",
                "sample.png",
                "image/png",
                imgOut.toByteArray()
        );

        when(ollamaVisionService.generateJson(
                any(byte[].class), eq("image/png"), anyString(), anyMap()))
                .thenReturn(new com.excel.reconciler.service.OllamaVisionService.JsonResponse(
                        200,
                        """
                        {
                          "isExcelTable": false,
                          "isBarcodeImage": true,
                          "rejectionReason": "Barcode images are not supported in the Excel section."
                        }
                        """,
                        null));

        mockMvc.perform(multipart("/api/v1/barcodes/reconcile")
                        .file(barcodeInExcelSlot)
                        .file(imgFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Barcode images are not supported")));
    }

    @Autowired
    private com.excel.reconciler.service.ExcelImageExtractorService excelImageExtractorService;

    @Autowired
    private com.excel.reconciler.service.ExcelHighlightService excelHighlightService;

    @Test
    public void testBuildWorkbookFromExtractedTable() throws Exception {
        List<String> headers = List.of("Item ID", "Product Name", "QR Barcode", "Category", "Price");
        List<List<String>> rows = List.of(
                List.of("101", "Gaming Laptop", "840192837401", "Computers", "$1200"),
                List.of("102", "Wireless Mouse", "719283049518", "Accessories", "$45")
        );

        byte[] workbookBytes = excelImageExtractorService.buildWorkbookBytes("Extracted Inventory", headers, rows);
        org.junit.jupiter.api.Assertions.assertNotNull(workbookBytes);
        org.junit.jupiter.api.Assertions.assertTrue(workbookBytes.length > 0);

        // Highlight matching against code "840192837401"
        try (java.io.InputStream is = new java.io.ByteArrayInputStream(workbookBytes)) {
            var result = excelHighlightService.highlightMatches(is, java.util.Set.of("840192837401"), "QR Barcode", false);
            org.junit.jupiter.api.Assertions.assertEquals(2, result.getTotalRows());
            org.junit.jupiter.api.Assertions.assertEquals(1, result.getMatchedRowsCount());
            org.junit.jupiter.api.Assertions.assertTrue(result.getMatchedCodes().contains("840192837401"));
            org.junit.jupiter.api.Assertions.assertEquals("Extracted Inventory", result.getActiveSheetName());
        }
    }

    @Test
    public void testParseOllamaResponseMultiFormat() throws Exception {
        // Test 1: Object array with column keys
        String jsonObjects = """
                {
                    "isExcelTable": true,
                    "isBarcodeImage": false,
                    "sheetName": "Logistics Table",
                    "headers": ["Number", "Waybill Number", "Client Name"],
                    "rows": [
                        {"Number": "1", "Waybill Number": "J01394871642", "Client Name": "Alice"},
                        {"Number": "2", "Waybill Number": "01400726571", "Client Name": "Bob"}
                    ]
                }
                """;
        var data1 = excelImageExtractorService.parseOllamaResponse(jsonObjects);
        org.junit.jupiter.api.Assertions.assertNotNull(data1);
        org.junit.jupiter.api.Assertions.assertEquals(3, data1.getHeaders().size());
        org.junit.jupiter.api.Assertions.assertEquals(2, data1.getRows().size());
        org.junit.jupiter.api.Assertions.assertEquals("J01394871642", data1.getRows().get(0).get(1));

        // Test 2: Objects with "values" array
        String jsonValues = """
                {
                    "isExcelTable": true,
                    "isBarcodeImage": false,
                    "sheetName": "Logistics Table",
                    "headers": ["Number", "Waybill Number", "Client Name"],
                    "rows": [
                        {"values": ["1", "J01394871642", "Alice"]},
                        {"values": ["2", "01400726571", "Bob"]}
                    ]
                }
                """;
        var data2 = excelImageExtractorService.parseOllamaResponse(jsonValues);
        org.junit.jupiter.api.Assertions.assertNotNull(data2);
        org.junit.jupiter.api.Assertions.assertEquals(2, data2.getRows().size());

        // Test 3: Reconcile extracted bytes with alphanumeric waybill matching
        try (java.io.InputStream is = new java.io.ByteArrayInputStream(data1.getExcelBytes())) {
            // Decoded scan has without 'J': "01394871642"
            var result = excelHighlightService.highlightMatches(is, java.util.Set.of("01394871642"), null, false);
            org.junit.jupiter.api.Assertions.assertEquals(2, result.getTotalRows());
            org.junit.jupiter.api.Assertions.assertEquals(1, result.getMatchedRowsCount());
            org.junit.jupiter.api.Assertions.assertEquals("Waybill Number", result.getResolvedColumnName());
        }
    }

    private static byte[] createQrCode(String value) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(value, BarcodeFormat.QR_CODE, 200, 200);
        ByteArrayOutputStream imgOut = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", imgOut);
        return imgOut.toByteArray();
    }
}
