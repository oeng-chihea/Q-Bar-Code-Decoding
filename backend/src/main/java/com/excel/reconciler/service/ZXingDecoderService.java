package com.excel.reconciler.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
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
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.ITF,
                BarcodeFormat.CODABAR,
                BarcodeFormat.AZTEC,
                BarcodeFormat.PDF_417
        ));
    }

    public List<Result> decode(byte[] imageBytes) {
        Map<String, Result> uniqueResults = new LinkedHashMap<>();
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            BufferedImage original = ImageIO.read(is);
            if (original == null) {
                return Collections.emptyList();
            }

            // Generate multi-scale and cropped candidates for challenging scenes & textures
            List<BufferedImage> candidateImages = generateCandidateImages(original);

            for (BufferedImage candidate : candidateImages) {
                // Pass A: Standard Hybrid Binarizer
                collectResults(candidate, false, uniqueResults);
                if (!uniqueResults.isEmpty()) break;

                // Pass B: Inverted Luminance (for dark-background/light-code)
                collectResults(candidate, true, uniqueResults);
                if (!uniqueResults.isEmpty()) break;

                // Pass C: Contrast & Sharpness Enhanced
                BufferedImage enhanced = enhanceContrastAndSharpen(candidate);
                collectResults(enhanced, false, uniqueResults);
                if (!uniqueResults.isEmpty()) break;

                // Pass D: Global Histogram Binarizer
                collectGlobalHistogram(candidate, uniqueResults);
                if (!uniqueResults.isEmpty()) break;

                // Pass E: Rotated 90, 180, 270 degrees
                for (int angle : new int[]{90, 180, 270}) {
                    BufferedImage rot = rotateImage(candidate, angle);
                    collectResults(rot, false, uniqueResults);
                    if (!uniqueResults.isEmpty()) break;
                }
                if (!uniqueResults.isEmpty()) break;
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

        // 1. Original (or normalized if giant camera photo > 2000px)
        if (w > 2000 || h > 2000) {
            double scale = 1600.0 / Math.max(w, h);
            list.add(resizeImage(src, (int)(w * scale), (int)(h * scale)));
        }
        list.add(src);

        // 2. Auto-detect white/high-contrast label sticker region
        BufferedImage labelRegion = extractStickerRegion(src);
        if (labelRegion != null) {
            list.add(labelRegion);
            // Also add scaled versions of the label
            if (labelRegion.getWidth() < 400 || labelRegion.getHeight() < 400) {
                list.add(resizeImage(labelRegion, labelRegion.getWidth() * 2, labelRegion.getHeight() * 2));
            }
        }

        // 3. Center & Quadrant Crops (for boxes photographed in room/bench)
        list.add(crop(src, 0.1, 0.1, 0.8, 0.8)); // Center 80%
        list.add(crop(src, 0.2, 0.2, 0.6, 0.6)); // Center 60%
        list.add(crop(src, 0.15, 0.2, 0.45, 0.6)); // Center-Left (common for QR)
        list.add(crop(src, 0.4, 0.2, 0.5, 0.6));  // Center-Right (common for 1D barcode)
        list.add(crop(src, 0.2, 0.35, 0.6, 0.55)); // Lower-Center

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
                    return;
                }
            } catch (ReaderException ignored) {
            }

            // Fallback single decode
            try {
                Result single = multiReader.decode(bitmap, hints);
                if (single != null && single.getText() != null && !single.getText().trim().isEmpty()) {
                    resultMap.putIfAbsent(single.getText().trim(), single);
                }
            } catch (ReaderException ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    private void collectGlobalHistogram(BufferedImage image, Map<String, Result> resultMap) {
        if (image == null) return;
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
            MultiFormatReader multiReader = new MultiFormatReader();
            Result single = multiReader.decode(bitmap, hints);
            if (single != null && single.getText() != null && !single.getText().trim().isEmpty()) {
                resultMap.putIfAbsent(single.getText().trim(), single);
            }
        } catch (Exception ignored) {
        }
    }

    private BufferedImage extractStickerRegion(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int minX = w, maxX = 0, minY = h, maxY = 0;
        int brightPixelCount = 0;

        int step = Math.max(2, Math.min(w, h) / 300);
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // Bright white sticker detection (R, G, B all > 190)
                if (r > 190 && g > 190 && b > 190) {
                    brightPixelCount++;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (brightPixelCount > 100 && maxX > minX + 50 && maxY > minY + 50) {
            int pad = 25;
            int x0 = Math.max(0, minX - pad);
            int y0 = Math.max(0, minY - pad);
            int rw = Math.min(w - x0, (maxX - minX) + 2 * pad);
            int rh = Math.min(h - y0, (maxY - minY) + 2 * pad);
            return src.getSubimage(x0, y0, rw, rh);
        }
        return null;
    }

    private BufferedImage enhanceContrastAndSharpen(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        // Contrast Stretch
        RescaleOp rescale = new RescaleOp(1.4f, -20, null);
        BufferedImage contrastImg = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        rescale.filter(gray, contrastImg);
        return contrastImg;
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

    private BufferedImage rotateImage(BufferedImage img, double angleDegrees) {
        double rads = Math.toRadians(angleDegrees);
        double sin = Math.abs(Math.sin(rads));
        double cos = Math.abs(Math.cos(rads));
        int w = (int) Math.floor(img.getWidth() * cos + img.getHeight() * sin);
        int h = (int) Math.floor(img.getHeight() * cos + img.getWidth() * sin);
        BufferedImage rotated = new BufferedImage(w, h, img.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : img.getType());
        AffineTransform at = new AffineTransform();
        at.translate((w - img.getWidth()) / 2.0, (h - img.getHeight()) / 2.0);
        at.rotate(rads, img.getWidth() / 2.0, img.getHeight() / 2.0);
        AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        return op.filter(img, rotated);
    }
}
