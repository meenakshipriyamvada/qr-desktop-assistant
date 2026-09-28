package com.qrassistant.capture;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * One screenshot of one monitor, plus where that monitor is on the desktop.
 *
 * Two coordinate systems meet here:
 *  - screenBounds is in LOGICAL pixels (what Windows reports, e.g. 1920 x 1080 at 150% scaling)
 *  - image is in PHYSICAL pixels (the real screen pixels, e.g. 2880 x 1620)
 *
 * The scale between them is worked out from the actual sizes, not assumed.
 *
 * "Overlay coordinates" below = logical pixels measured from this monitor's top-left corner.
 * That is exactly what the full-screen overlay window uses for mouse positions.
 * The methods here are the ONLY place that converts between overlay and image coordinates. (A3)
 *
 * SECURITY: the image may contain anything that was on screen.
 * It stays in memory only - never saved, logged or sent anywhere.
 */
public record CapturedScreen(BufferedImage image, Rectangle screenBounds) {

    /** "Compact constructor": runs before the fields are set, so we can validate the inputs. */
    public CapturedScreen {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(screenBounds, "screenBounds");
        if (screenBounds.width <= 0 || screenBounds.height <= 0) {
            throw new IllegalArgumentException("screenBounds must have a positive size");
        }
        // Rectangle is mutable, so keep our own copy; nobody outside can change it later.
        screenBounds = new Rectangle(screenBounds);
    }

    /** Returns a copy, for the same reason as above. */
    @Override
    public Rectangle screenBounds() {
        return new Rectangle(screenBounds);
    }

    /** Physical pixels per logical pixel, horizontally. 1.0 at 100% scaling, 1.5 at 150%. */
    public double scaleX() {
        return (double) image.getWidth() / screenBounds.width;
    }

    /** Physical pixels per logical pixel, vertically. */
    public double scaleY() {
        return (double) image.getHeight() / screenBounds.height;
    }

    // --- Converting positions (the only place this maths lives) ---

    /** Overlay position -> image pixel. */
    public double toImageX(double overlayX) {
        return overlayX * scaleX();
    }

    public double toImageY(double overlayY) {
        return overlayY * scaleY();
    }

    /** Image pixel -> overlay position. */
    public double toOverlayX(double imageX) {
        return imageX / scaleX();
    }

    public double toOverlayY(double imageY) {
        return imageY / scaleY();
    }

    /**
     * Turns a box dragged on the overlay (from one corner to the opposite one, in any direction)
     * into the matching area of the image. The result is clipped to the image, so it may be empty.
     */
    public Rectangle overlayBoxToImageArea(double x1, double y1, double x2, double y2) {
        int left = (int) Math.floor(toImageX(Math.min(x1, x2)));
        int top = (int) Math.floor(toImageY(Math.min(y1, y2)));
        int right = (int) Math.ceil(toImageX(Math.max(x1, x2)));
        int bottom = (int) Math.ceil(toImageY(Math.max(y1, y2)));
        Rectangle area = new Rectangle(left, top, right - left, bottom - top);
        return area.intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
    }

    /**
     * The part of the screenshot inside the given image area.
     * Note: getSubimage() shares pixels with the original instead of copying them. That's fine,
     * because nobody changes the screenshot - and it saves memory.
     */
    public BufferedImage crop(Rectangle imageArea) {
        Rectangle safe = imageArea.intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        if (safe.isEmpty()) {
            throw new IllegalArgumentException("Area is outside the screenshot");
        }
        return image.getSubimage(safe.x, safe.y, safe.width, safe.height);
    }
}