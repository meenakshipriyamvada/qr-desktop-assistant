package com.qrassistant.scan;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Makes an image easier for the QR reader, and remembers how, so positions can be mapped back.
 *
 * Two fixes from the design review:
 *  - D2: adds a white border. QR codes need empty space around them (the "quiet zone");
 *        a tightly drawn box cuts it off and the reader fails.
 *  - D1: enlarges small images. In a small crop, each QR square may be only 1-2 pixels,
 *        too small to measure. Enlarging gives the reader more pixels to work with.
 */
public final class ScanPreprocessor {

    /** Images whose longer side is below these sizes get enlarged. */
    private static final int ENLARGE_3X_BELOW = 300;
    private static final int ENLARGE_2X_BELOW = 600;

    /** Border size: a tenth of the (enlarged) image's longer side, kept between these limits. */
    private static final int MIN_BORDER = 20;
    private static final int MAX_BORDER = 100;   // a full screen doesn't need a huge border

    /**
     * The prepared image plus what was done to it.
     *
     * @param image  the new, bigger image (with border)
     * @param scale  how much the original was enlarged (1, 2 or 3)
     * @param border width of the white border added on every side, in prepared-image pixels
     */
    public record Prepared(BufferedImage image, int scale, int border) {

        /** Converts a position in the prepared image back to the same position in the ORIGINAL image. */
        public ImagePoint toOriginal(ImagePoint p) {
            return new ImagePoint((p.x() - border) / scale, (p.y() - border) / scale);
        }
    }

    public Prepared prepare(BufferedImage original) {
        Objects.requireNonNull(original, "original");

        int longerSide = Math.max(original.getWidth(), original.getHeight());
        int scale = longerSide < ENLARGE_3X_BELOW ? 3
                : longerSide < ENLARGE_2X_BELOW ? 2
                : 1;

        int scaledWidth = original.getWidth() * scale;
        int scaledHeight = original.getHeight() * scale;
        int border = Math.clamp(Math.max(scaledWidth, scaledHeight) / 10, MIN_BORDER, MAX_BORDER);

        BufferedImage prepared = new BufferedImage(
                scaledWidth + 2 * border, scaledHeight + 2 * border, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = prepared.createGraphics();
        try {
            // White background = the border.
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, prepared.getWidth(), prepared.getHeight());

            // "Nearest neighbour" enlarging turns each pixel into a sharp 2x2 or 3x3 block.
            // Smoother methods would blur the edges between black and white QR squares.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(original, border, border, scaledWidth, scaledHeight, null);
        } finally {
            g.dispose();
        }
        return new Prepared(prepared, scale, border);
    }
}