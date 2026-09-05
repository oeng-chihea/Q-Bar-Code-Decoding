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

        // 3. Generate sample Excel Table Image (photo/screenshot of spreadsheet table)
        int imgW = 900;
        int imgH = 400;
        java.awt.image.BufferedImage tableImg = new java.awt.image.BufferedImage(imgW, imgH, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D tg = tableImg.createGraphics();
        tg.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        tg.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);

        // White background
        tg.setColor(java.awt.Color.WHITE);
        tg.fillRect(0, 0, imgW, imgH);

        // Excel green top title banner
        tg.setColor(new java.awt.Color(16, 124, 65));
        tg.fillRect(0, 0, imgW, 40);
        tg.setColor(java.awt.Color.WHITE);
        tg.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 16));
        tg.drawString("Warehouse Inventory Sheet - Excel Data Table", 20, 26);

        // Column coordinates
        int startX = 20;
        int startY = 60;
        int rowHeight = 32;
        int[] colWidths = {90, 240, 180, 160, 100};
        String[] tableHeaders = {"Item ID", "Product Name", "QR Barcode", "Category", "Price"};

        // Draw header row
        tg.setColor(new java.awt.Color(235, 240, 237));
        tg.fillRect(startX, startY, 770, rowHeight);
        tg.setColor(new java.awt.Color(30, 30, 30));
        tg.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));

        int curX = startX;
        for (int c = 0; c < tableHeaders.length; c++) {
            tg.drawString(tableHeaders[c], curX + 10, startY + 21);
            tg.setColor(new java.awt.Color(200, 205, 205));
            tg.drawRect(curX, startY, colWidths[c], rowHeight);
            tg.setColor(new java.awt.Color(30, 30, 30));
            curX += colWidths[c];
        }

        // Draw rows
        int curY = startY + rowHeight;
        tg.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        for (int i = 0; i < Math.min(8, items.size()); i++) {
            SampleItem item = items.get(i);
            String[] rowVals = {item.id(), item.name(), item.barcode(), item.category(), item.price()};
            curX = startX;

            if (i % 2 == 1) {
                tg.setColor(new java.awt.Color(248, 249, 250));
                tg.fillRect(startX, curY, 770, rowHeight);
            }

            tg.setColor(new java.awt.Color(50, 50, 50));
            for (int c = 0; c < rowVals.length; c++) {
                tg.drawString(rowVals[c], curX + 10, curY + 20);
                tg.setColor(new java.awt.Color(220, 222, 224));
                tg.drawRect(curX, curY, colWidths[c], rowHeight);
                tg.setColor(new java.awt.Color(50, 50, 50));
                curX += colWidths[c];
            }
            curY += rowHeight;
        }

        tg.dispose();

        File sampleTableImgFile = baseDir.resolve("sample_excel_table_image.png").toFile();
        javax.imageio.ImageIO.write(tableImg, "PNG", sampleTableImgFile);

        assertTrue(excelFile.exists());
        assertTrue(sampleTableImgFile.exists());
    }

    @Test
    public void generateUltraSharp7ItemsWarehouseImage() throws Exception {
        Path inputPath = Paths.get("..", "sample-data", "warehouse_7items_sharp_barcodes_1788199907445.jpg");
        if (!Files.exists(inputPath) || !Files.isReadable(inputPath)) {
            return;
        }

        java.awt.image.BufferedImage img;
        try {
            img = javax.imageio.ImageIO.read(inputPath.toFile());
        } catch (Exception e) {
            return;
        }
        if (img == null) {
            return;
        }
        int w = img.getWidth();
        int h = img.getHeight();

        java.awt.Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        MultiFormatWriter writer = new MultiFormatWriter();

        // 7 items with exact label placements (ratios of image width & height)
        record LabelSpec(double xRatio, double yRatio, double wRatio, double hRatio, String barcode, String name, String sku, boolean isTag, boolean hasQr) {}

        List<LabelSpec> labels = List.of(
                new LabelSpec(0.128, 0.690, 0.055, 0.120, "840192837401", "Orthopedic Dog Bed", "PET-020", true, false),
                new LabelSpec(0.233, 0.700, 0.082, 0.110, "840192837418", "Smart Home Hub", "ELE-034", false, false),
                new LabelSpec(0.362, 0.630, 0.058, 0.125, "719283049518", "Backpacking Tent (2P)", "OUT-110", false, true),
                new LabelSpec(0.455, 0.705, 0.078, 0.098, "638201948512", "Travel Power Hub", "PWR-099", false, false),
                new LabelSpec(0.590, 0.620, 0.088, 0.095, "840192837425", "Wireless Headphones", "AUD-055", false, false),
                new LabelSpec(0.762, 0.525, 0.048, 0.100, "840192837432", "Insulated Travel Bottle", "SPT-056", true, false),
                new LabelSpec(0.842, 0.650, 0.088, 0.145, "840192837449", "Universal Tool Box", "TOOL-060", false, true)
        );

        for (LabelSpec spec : labels) {
            int lx = (int) (w * spec.xRatio);
            int ly = (int) (h * spec.yRatio);
            int lw = (int) (w * spec.wRatio);
            int lh = (int) (h * spec.hRatio);

            // Draw clean white / kraft sticker background
            if (spec.isTag) {
                g2d.setColor(new java.awt.Color(218, 195, 168)); // Kraft cardboard tag
                g2d.fillRoundRect(lx, ly, lw, lh, 8, 8);
                g2d.setColor(new java.awt.Color(160, 140, 115));
                g2d.drawRoundRect(lx, ly, lw, lh, 8, 8);
            } else {
                g2d.setColor(java.awt.Color.WHITE);
                g2d.fillRoundRect(lx, ly, lw, lh, 6, 6);
                g2d.setColor(new java.awt.Color(220, 220, 220));
                g2d.drawRoundRect(lx, ly, lw, lh, 6, 6);
            }

            int innerPad = 4;
            int curY = ly + 14;

            // Small header text
            g2d.setColor(new java.awt.Color(40, 40, 40));
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, Math.max(9, lw / 15)));
            if (lw > 80) {
                String title = spec.name.length() > 18 ? spec.name.substring(0, 18) : spec.name;
                g2d.drawString(title, lx + innerPad, curY);
                curY += 12;
            }

            if (spec.hasQr) {
                // Draw QR Code on left, Barcode on right or stacked
                int qrSize = Math.min(lw / 2 - innerPad, lh - (curY - ly) - 10);
                if (qrSize > 25) {
                    BitMatrix qrMatrix = writer.encode(spec.barcode, BarcodeFormat.QR_CODE, qrSize, qrSize);
                    java.awt.image.BufferedImage qrImg = MatrixToImageWriter.toBufferedImage(qrMatrix);
                    g2d.drawImage(qrImg, lx + innerPad, curY, null);

                    int bcWidth = lw - qrSize - (innerPad * 3);
                    int bcHeight = qrSize - 16;
                    if (bcWidth > 35 && bcHeight > 15) {
                        BitMatrix bcMatrix = writer.encode(spec.barcode, BarcodeFormat.CODE_128, bcWidth, bcHeight);
                        java.awt.image.BufferedImage bcImg = MatrixToImageWriter.toBufferedImage(bcMatrix);
                        g2d.drawImage(bcImg, lx + qrSize + innerPad * 2, curY, null);

                        g2d.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, Math.max(10, lw / 14)));
                        g2d.setColor(java.awt.Color.BLACK);
                        g2d.drawString(spec.barcode, lx + qrSize + innerPad * 2, curY + bcHeight + 12);
                    }
                }
            } else {
                // Draw 1D Barcode with large crisp numbers below
                int bcHeight = Math.max(24, lh - (curY - ly) - 20);
                int bcWidth = lw - (innerPad * 2);
                BitMatrix bcMatrix = writer.encode(spec.barcode, BarcodeFormat.CODE_128, bcWidth, bcHeight);
                java.awt.image.BufferedImage bcImg = MatrixToImageWriter.toBufferedImage(bcMatrix);
                g2d.drawImage(bcImg, lx + innerPad, curY, null);

                // Large crystal-clear bold barcode number centered below barcode
                g2d.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, Math.max(11, lw / 12)));
                g2d.setColor(java.awt.Color.BLACK);
                java.awt.FontMetrics fm = g2d.getFontMetrics();
                int textW = fm.stringWidth(spec.barcode);
                int textX = lx + (lw - textW) / 2;
                g2d.drawString(spec.barcode, textX, curY + bcHeight + fm.getAscent() + 2);
            }
        }

        g2d.dispose();

        Path out1 = Paths.get("..", "sample-data", "warehouse_7_items_sharp.jpg");
        Path out2 = Paths.get("..", "sample-data", "images", "warehouse_7_items_sharp.jpg");

        javax.imageio.ImageIO.write(img, "JPEG", out1.toFile());
        javax.imageio.ImageIO.write(img, "JPEG", out2.toFile());

        assertTrue(Files.exists(out1));
    }
}
