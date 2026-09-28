package com.qrassistant.scan;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * QrScanner implemented with ZXing. This is the ONLY class in the app that imports ZXing,
 * so swapping libraries later means rewriting just this file.
 *
 * Finds ALL QR codes in an image:
 *  1. prepares the image (white border, enlarge if small)
 *  2. looks for normal dark-on-light codes AND light-on-dark codes
 *  3. maps every position back to the caller's original image
 *  4. removes duplicates and sorts the codes in reading order
 */
public class ZxingQrScanner implements QrScanner {

    /** Settings for ZXing, created once and never changed. */
    private static final Map<DecodeHintType, Object> HINTS = createHints();

    /** Codes whose centres are within this many pixels vertically count as "the same row" when sorting. */
    private static final int ROW_HEIGHT = 100;

    private final ScanPreprocessor preprocessor = new ScanPreprocessor();

    private static Map<DecodeHintType, Object> createHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);   // slower but finds harder codes
        return Collections.unmodifiableMap(hints);
    }

    @Override
    public List<DecodedQr> scan(BufferedImage image) {
        Objects.requireNonNull(image, "image");

        ScanPreprocessor.Prepared prepared = preprocessor.prepare(image);
        LuminanceSource luminance = new BufferedImageLuminanceSource(prepared.image());

        // Both passes every time: one screen can show a normal code AND a dark-mode one.
        List<DecodedQr> found = new ArrayList<>();
        found.addAll(decodeAll(luminance, prepared));            // dark-on-light (normal)
        found.addAll(decodeAll(luminance.invert(), prepared));   // light-on-dark (D4)

        return sortInReadingOrder(removeDuplicates(found));
    }

    /** One pass: every code ZXing can find in these brightness values. */
    private static List<DecodedQr> decodeAll(LuminanceSource luminance, ScanPreprocessor.Prepared prepared) {
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(luminance));

        // QRCodeMultiReader keeps searching after the first code. New reader each time:
        // ZXing readers are not documented as safe to share between threads.
        QRCodeMultiReader reader = new QRCodeMultiReader();
        Result[] results;
        try {
            results = reader.decodeMultiple(bitmap, HINTS);
        } catch (NotFoundException e) {
            return List.of();   // nothing found in this pass - normal, not an error
        }

        List<DecodedQr> codes = new ArrayList<>();
        for (Result result : results) {
            codes.add(toDecodedQr(result, prepared));
        }
        return codes;
    }

    /** Converts ZXing's result into our own type, with positions in the ORIGINAL image. */
    private static DecodedQr toDecodedQr(Result result, ScanPreprocessor.Prepared prepared) {
        List<ImagePoint> points = new ArrayList<>();
        ResultPoint[] zxingPoints = result.getResultPoints();
        if (zxingPoints != null) {
            for (ResultPoint p : zxingPoints) {
                if (p != null) {
                    points.add(prepared.toOriginal(new ImagePoint(p.getX(), p.getY())));
                }
            }
        }
        return new DecodedQr(result.getText(), points);
    }

    /**
     * Same text = same code (D7). This happens when both passes find it, or when
     * the same QR is on screen twice (e.g. a slide and its thumbnail) - either one
     * leads to exactly the same result, so we keep the first.
     */
    private static List<DecodedQr> removeDuplicates(List<DecodedQr> codes) {
        Map<String, DecodedQr> byText = new LinkedHashMap<>();   // keeps the original order
        for (DecodedQr code : codes) {
            byText.putIfAbsent(code.text(), code);
        }
        return new ArrayList<>(byText.values());
    }

    /** Top-to-bottom, then left-to-right, so "QR 1, QR 2, ..." matches how you'd read the screen. */
    private static List<DecodedQr> sortInReadingOrder(List<DecodedQr> codes) {
        codes.sort(Comparator
                .comparingInt((DecodedQr code) -> (int) (centreOf(code).y() / ROW_HEIGHT))
                .thenComparingDouble(code -> centreOf(code).x()));
        return List.copyOf(codes);
    }

    /** Average of the location points; good enough to know roughly where a code is. */
    private static ImagePoint centreOf(DecodedQr code) {
        if (code.points().isEmpty()) {
            return new ImagePoint(0, 0);
        }
        double sumX = 0;
        double sumY = 0;
        for (ImagePoint p : code.points()) {
            sumX += p.x();
            sumY += p.y();
        }
        return new ImagePoint(sumX / code.points().size(), sumY / code.points().size());
    }
}