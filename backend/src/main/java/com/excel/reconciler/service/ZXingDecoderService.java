package com.excel.reconciler.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.List;

@Service
public class ZXingDecoderService {
    private static final Logger log = LoggerFactory.getLogger(ZXingDecoderService.class);

    private final Map<DecodeHintType, Object> hints;

    public ZXingDecoderService() {
        hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.CODE_128,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E
        ));
    }

    public List<Result> decode(byte[] imageBytes) {
        Map<String, Result> uniqueResults = new LinkedHashMap<>();
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            BufferedImage original = ImageIO.read(is);
            if (original == null) {
                return Collections.emptyList();
            }

            // Use a small, prioritized candidate set for real package photos. Every candidate
            // is still part of the single ZXing stage; Ollama is only called if all candidates fail.
            List<BufferedImage> candidateImages = generateCandidateImages(original);

            for (BufferedImage candidate : candidateImages) {
                collectResults(candidate, false, uniqueResults);
                if (!uniqueResults.isEmpty()) break;
            }

            // If normal passes found nothing, try inverted (dark background / light barcode) on primary image
            if (uniqueResults.isEmpty() && !candidateImages.isEmpty()) {
                collectResults(candidateImages.get(0), true, uniqueResults);
            }

        } catch (Exception e) {
            log.debug("ZXing decoding exception: {}", e.getMessage());
        }
        return new ArrayList<>(uniqueResults.values());
    }

    private List<BufferedImage> generateCandidateImages(BufferedImage src) {
        List<BufferedImage> list = new ArrayList<>();
        int w = src.getWidth();
        int h = src.getHeight();

        // 1. Start with a normalized full-frame image. Large phone photos are reduced once
        // to keep 40+ image batches responsive while preserving the original as a fallback.
        if (w > 2000 || h > 2000) {
            double scale = 1600.0 / Math.max(w, h);
            BufferedImage resized = resizeImage(src, (int)(w * scale), (int)(h * scale));
            list.add(resized);
            // Prioritize fast crops of the normalized image before attempting the raw huge image
            list.add(crop(resized, 0.05, 0.05, 0.90, 0.60));
            list.add(crop(resized, 0.05, 0.35, 0.90, 0.60));
            list.add(src);
        } else {
            list.add(src);
            BufferedImage scanSource = list.get(0);
            list.add(crop(scanSource, 0.05, 0.05, 0.90, 0.60));
            list.add(crop(scanSource, 0.05, 0.35, 0.90, 0.60));
        }

        return list;
    }

    private void collectResults(BufferedImage image, boolean invert, Map<String, Result> resultMap) {
        if (image == null) return;
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            if (invert) {
                source = source.invert();
            }
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            MultiFormatReader multiReader = new MultiFormatReader();
            GenericMultipleBarcodeReader multiBarcodeReader = new GenericMultipleBarcodeReader(multiReader);

            try {
                Result[] results = multiBarcodeReader.decodeMultiple(bitmap, hints);
                if (results != null && results.length > 0) {
                    for (Result r : results) {
                        if (r != null && r.getText() != null && !r.getText().trim().isEmpty()) {
                            resultMap.putIfAbsent(r.getText().trim(), r);
                        }
                    }
                }
            } catch (ReaderException ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    private BufferedImage crop(BufferedImage img, double xRatio, double yRatio, double wRatio, double hRatio) {
        int x = (int) (img.getWidth() * xRatio);
        int y = (int) (img.getHeight() * yRatio);
        int w = (int) (img.getWidth() * wRatio);
        int h = (int) (img.getHeight() * hRatio);
        x = Math.max(0, Math.min(x, img.getWidth() - 10));
        y = Math.max(0, Math.min(y, img.getHeight() - 10));
        w = Math.min(w, img.getWidth() - x);
        h = Math.min(h, img.getHeight() - y);
        return img.getSubimage(x, y, w, h);
    }

    private BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, original.getType() == 0 ? BufferedImage.TYPE_INT_RGB : original.getType());
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return resized;
    }

}
