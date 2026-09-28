package com.qrassistant.capture;

import java.awt.AWTException;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.Comparator;

/**
 * Takes a full-resolution screenshot of the monitor the mouse is on.
 *
 * This class knows nothing about QR codes or windows: pixels in, pixels out.
 * Hiding the app's own windows before capturing is the caller's job.
 *
 * THREADING: only the scan worker thread uses this class, so no locking is needed.
 */
public class ScreenCapturer {

    /** Created on first use, so a failed setup is simply retried on the next capture. */
    private Robot robot;

    /**
     * Captures the whole monitor that currently contains the mouse pointer.
     *
     * @throws ScreenCaptureException if this machine can't capture the screen (right now)
     */
    public CapturedScreen captureMonitorUnderMouse() throws ScreenCaptureException {
        Robot robot = robot();
        GraphicsDevice monitor = findMonitorUnderMouse();

        // The monitor's position and size in LOGICAL pixels (see CapturedScreen).
        Rectangle bounds = monitor.getDefaultConfiguration().getBounds();

        // A normal createScreenCapture() would shrink the image to logical size on a
        // scaled display, blurring small QR codes. The multi-resolution version gives us
        // several versions of the same screenshot; we keep the biggest (= physical pixels).
        MultiResolutionImage allSizes = robot.createMultiResolutionScreenCapture(bounds);
        Image largest = allSizes.getResolutionVariants().stream()
                .max(Comparator.comparingInt(variant -> variant.getWidth(null)))
                .orElseThrow(() -> new IllegalStateException("Screen capture returned no image"));

        return new CapturedScreen(toBufferedImage(largest), bounds);
    }

    /** Returns the Robot, creating it the first time (or again, if earlier attempts failed). */
    private Robot robot() throws ScreenCaptureException {
        if (robot == null) {
            if (GraphicsEnvironment.isHeadless()) {
                throw new ScreenCaptureException("Java is running without a display (headless mode).", null);
            }
            try {
                robot = new Robot();
            } catch (AWTException | SecurityException e) {
                throw new ScreenCaptureException("Windows did not allow screen capture.", e);
            }
        }
        return robot;
    }

    /** The monitor the mouse is on; falls back to the main monitor if Windows can't tell us. */
    private static GraphicsDevice findMonitorUnderMouse() {
        PointerInfo pointer = MouseInfo.getPointerInfo();   // can be null, e.g. no mouse attached
        if (pointer != null && pointer.getDevice() != null) {
            return pointer.getDevice();
        }
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    }

    /**
     * The variants are declared as the general java.awt.Image type.
     * On Windows they are already BufferedImages; if not, we copy the pixels into one.
     */
    private static BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage buffered) {
            return buffered;
        }
        BufferedImage copy = new BufferedImage(
                image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();   // release the drawing resources
        }
        return copy;
    }
}