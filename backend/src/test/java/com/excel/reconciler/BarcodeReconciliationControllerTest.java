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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class BarcodeReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
}
