package com.excel.reconciler;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SampleDataGeneratorTest {

    record SampleItem(String id, String name, String barcode, String category, String price, boolean generateImage, BarcodeFormat format) {}

    @Test
    public void generateSampleDataset() throws Exception {
        Path baseDir = Paths.get("..", "sample-data");
        Path imagesDir = baseDir.resolve("images");
        Files.createDirectories(imagesDir);

        List<SampleItem> items = List.of(
                new SampleItem("1001", "MacBook Pro M3", "PROD-101", "Laptops", "$1999", true, BarcodeFormat.QR_CODE),
                new SampleItem("1002", "Dell UltraSharp 27\"", "PROD-102", "Monitors", "$549", true, BarcodeFormat.CODE_128),
                new SampleItem("1003", "Logitech MX Master 3S", "PROD-103", "Accessories", "$99", true, BarcodeFormat.QR_CODE),
                new SampleItem("1004", "Keychron K2 Keyboard", "PROD-104", "Accessories", "$89", true, BarcodeFormat.CODE_128),
                new SampleItem("1005", "Sony WH-1000XM5", "PROD-105", "Audio", "$399", true, BarcodeFormat.QR_CODE),
                new SampleItem("1006", "Anker 100W GaN Charger", "0012490", "Cables & Power", "$59", true, BarcodeFormat.CODE_128),
                new SampleItem("1007", "iPad Pro 11-inch", "PROD-107", "Tablets", "$799", false, null),
                new SampleItem("1008", "AirPods Pro 2", "PROD-108", "Audio", "$249", false, null),
                new SampleItem("1009", "CalDigit TS4 Thunderbolt Dock", "0098711", "Docks", "$399", true, BarcodeFormat.QR_CODE),
                new SampleItem("1010", "Samsung T7 2TB SSD", "PROD-110", "Storage", "$179", false, null)
        );

        // 1. Generate Excel File
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Warehouse Inventory");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Item ID");
        header.createCell(1).setCellValue("Product Name");
        header.createCell(2).setCellValue("QR Barcode");
        header.createCell(3).setCellValue("Category");
        header.createCell(4).setCellValue("Price");

        int r = 1;
        for (SampleItem item : items) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(item.id());
            row.createCell(1).setCellValue(item.name());
            row.createCell(2).setCellValue(item.barcode());
            row.createCell(3).setCellValue(item.category());
            row.createCell(4).setCellValue(item.price());
        }

        for (int i = 0; i < 5; i++) {
            sheet.autoSizeColumn(i);
        }

        File excelFile = baseDir.resolve("sample_inventory.xlsx").toFile();
        try (FileOutputStream fos = new FileOutputStream(excelFile)) {
            workbook.write(fos);
        }
        workbook.close();

        // 2. Generate Barcode / QR Images
        MultiFormatWriter writer = new MultiFormatWriter();
        for (SampleItem item : items) {
            if (item.generateImage()) {
                int width = item.format() == BarcodeFormat.QR_CODE ? 250 : 350;
                int height = item.format() == BarcodeFormat.QR_CODE ? 250 : 120;
                BitMatrix bitMatrix = writer.encode(item.barcode(), item.format(), width, height);

                File imgFile = imagesDir.resolve(item.barcode() + "_" + item.format().name().toLowerCase() + ".png").toFile();
                MatrixToImageWriter.writeToPath(bitMatrix, "PNG", imgFile.toPath());
            }
        }

        // Generate an extra unmatched barcode image
        BitMatrix unmatched = writer.encode("UNMATCHED-9999", BarcodeFormat.QR_CODE, 250, 250);
        File unmatchedFile = imagesDir.resolve("unmatched_extra.png").toFile();
        MatrixToImageWriter.writeToPath(unmatched, "PNG", unmatchedFile.toPath());

        assertTrue(excelFile.exists());
    }
}
