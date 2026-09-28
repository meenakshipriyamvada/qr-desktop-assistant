package com.qrassistant.scan;

import java.util.List;
import java.util.Objects;

/**
 * One QR code found in an image.
 *
 * @param text   the decoded content, exactly as stored in the QR code.
 *               UNTRUSTED: whoever made the QR code chose every character.
 * @param points where ZXing found the code, in image pixels. For QR codes these are the
 *               CENTRES of the three big corner squares (finder patterns), sometimes plus the
 *               small alignment square - not the outer corners of the code.
 */
public record DecodedQr(String text, List<ImagePoint> points) {

    public DecodedQr {
        Objects.requireNonNull(text, "text");
        points = List.copyOf(points);   // unmodifiable copy; also rejects null
    }

    /**
     * The same code with every point shifted. Used when a code was found in a CROPPED part of
     * the screenshot: shifting by the crop's top-left corner gives positions in the full screenshot.
     */
    public DecodedQr movedBy(double dx, double dy) {
        List<ImagePoint> moved = points.stream()
                .map(p -> new ImagePoint(p.x() + dx, p.y() + dy))
                .toList();
        return new DecodedQr(text, moved);
    }
}