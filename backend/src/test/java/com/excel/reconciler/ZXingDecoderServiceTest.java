package com.excel.reconciler;

import com.excel.reconciler.service.ZXingDecoderService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ZXingDecoderServiceTest {

    private final ZXingDecoderService decoderService = new ZXingDecoderService();

    @Test
    public void testDecodeQRCode() throws Exception {
        String testContent = "PROD-998877";
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(testContent, BarcodeFormat.QR_CODE, 200, 200);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        List<Result> results = decoderService.decode(imageBytes);

        assertFalse(results.isEmpty(), "Should decode generated QR code");
        assertEquals(testContent, results.get(0).getText());
        assertEquals(BarcodeFormat.QR_CODE, results.get(0).getBarcodeFormat());
    }

    @Test
    public void testDecodeInvertedQRCode() throws Exception {
        String testContent = "INVERTED-12345";
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(testContent, BarcodeFormat.QR_CODE, 200, 200);

        java.awt.image.BufferedImage img = MatrixToImageWriter.toBufferedImage(bitMatrix);
        // Invert colors: light background becomes dark, dark modules become light
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                img.setRGB(x, y, rgb ^ 0x00FFFFFF);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        List<Result> results = decoderService.decode(imageBytes);

        assertFalse(results.isEmpty(), "Should decode inverted QR code");
        assertEquals(testContent, results.get(0).getText());
    }

    @Test
    public void testDecodeHighResolutionImage() throws Exception {
        String testContent = "HIGHRES-PROD-456";
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(testContent, BarcodeFormat.QR_CODE, 300, 300);
        java.awt.image.BufferedImage qrImg = MatrixToImageWriter.toBufferedImage(bitMatrix);

        java.awt.image.BufferedImage largeImg = new java.awt.image.BufferedImage(2400, 2400, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = largeImg.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 2400, 2400);
        g.drawImage(qrImg, 200, 200, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(largeImg, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        List<Result> results = decoderService.decode(imageBytes);

        assertFalse(results.isEmpty(), "Should decode high resolution image barcode");
        assertEquals(testContent, results.get(0).getText());
    }
}
