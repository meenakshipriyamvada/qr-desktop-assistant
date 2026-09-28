package com.qrassistant.scan;

import java.util.ArrayList;
import java.util.List;

/**
 * Estimates the four outer corners of a QR code from ZXing's location points,
 * so the overlay can draw a box around it.
 *
 * ZXing gives the CENTRES of the three big corner squares, in this order:
 * bottom-left, top-left, top-right (sometimes plus the small alignment square).
 * Those centres sit a little INSIDE the code, so we:
 *  1. work out the missing 4th centre (bottom-right) - the four form a parallelogram
 *  2. push all four outwards from the middle until they reach the code's real edges
 *
 * This works for rotated codes too, because we never assume the code is upright.
 */
public final class QrGeometry {

    /**
     * How far to push the corners outwards. The exact value depends on the QR code's size
     * (1.5 for the smallest codes, ~1.2 for big ones); 1.35 is a good middle for drawing an outline.
     */
    private static final double EXPAND = 1.35;

    private QrGeometry() {
    }

    /**
     * @return the corners in order top-left, top-right, bottom-right, bottom-left (image pixels),
     *         or an empty list if ZXing gave fewer than 3 points
     */
    public static List<ImagePoint> estimateCorners(DecodedQr qr) {
        List<ImagePoint> p = qr.points();
        if (p.size() < 3) {
            return List.of();
        }
        ImagePoint bottomLeft = p.get(0);
        ImagePoint topLeft = p.get(1);
        ImagePoint topRight = p.get(2);
        // In a parallelogram, the 4th corner = topRight + bottomLeft - topLeft.
        ImagePoint bottomRight = new ImagePoint(
                topRight.x() + bottomLeft.x() - topLeft.x(),
                topRight.y() + bottomLeft.y() - topLeft.y());

        List<ImagePoint> centres = List.of(topLeft, topRight, bottomRight, bottomLeft);
        ImagePoint middle = average(centres);

        List<ImagePoint> corners = new ArrayList<>();
        for (ImagePoint c : centres) {
            corners.add(new ImagePoint(
                    middle.x() + (c.x() - middle.x()) * EXPAND,
                    middle.y() + (c.y() - middle.y()) * EXPAND));
        }
        return List.copyOf(corners);
    }

    private static ImagePoint average(List<ImagePoint> points) {
        double sumX = 0;
        double sumY = 0;
        for (ImagePoint point : points) {
            sumX += point.x();
            sumY += point.y();
        }
        return new ImagePoint(sumX / points.size(), sumY / points.size());
    }
}