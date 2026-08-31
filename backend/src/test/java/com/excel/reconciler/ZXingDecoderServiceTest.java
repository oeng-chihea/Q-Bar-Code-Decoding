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
}
