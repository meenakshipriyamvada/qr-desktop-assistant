package com.qrassistant.ui;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;

/**
 * Converts AWT images (what Robot and ZXing use) into JavaFX images (what windows can show).
 *
 * JavaFX has a ready-made converter (SwingFXUtils), but it needs an extra module.
 * Doing it by hand is a few lines and shows what "an image" really is: an array of pixels.
 */
public final class ImageConverter {

    private ImageConverter() {
        // utility class: only static methods, no objects
    }

    public static WritableImage toFxImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();

        // Every pixel as one int: 8 bits each of Alpha, Red, Green, Blue (ARGB).
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);

        WritableImage result = new WritableImage(width, height);
        result.getPixelWriter().setPixels(
                0, 0, width, height,
                PixelFormat.getIntArgbInstance(),
                pixels, 0, width);
        return result;
    }
}