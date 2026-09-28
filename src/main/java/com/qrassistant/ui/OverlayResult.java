package com.qrassistant.ui;

import com.qrassistant.scan.DecodedQr;

import java.awt.Rectangle;

/**
 * How the selection overlay ended. There are exactly three possibilities,
 * and "sealed" lets the compiler check that the workflow handles all three.
 */
public sealed interface OverlayResult {

    /** The user clicked one of the outlined codes. */
    record Picked(DecodedQr qr) implements OverlayResult {
    }

    /** The user dragged a box. The area is in IMAGE pixels, already clipped to the screenshot. */
    record Region(Rectangle area) implements OverlayResult {

        public Region {
            area = new Rectangle(area);   // Rectangle is mutable: keep our own copy
        }

        @Override
        public Rectangle area() {
            return new Rectangle(area);
        }
    }

    /** Esc, right-click, or the overlay was closed some other way. */
    record Cancelled() implements OverlayResult {
    }
}