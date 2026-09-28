package com.qrassistant.scan;

/**
 * A position inside an image, in image pixels (0,0 = top-left corner).
 *
 * We use our own tiny record instead of ZXing's ResultPoint or AWT's Point2D,
 * so the rest of the app never depends on ZXing, and the value can't be changed later.
 */
public record ImagePoint(double x, double y) {
}