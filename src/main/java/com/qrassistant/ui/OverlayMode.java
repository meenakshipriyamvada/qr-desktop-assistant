package com.qrassistant.ui;

/** What the selection overlay is asking the user to do. Each mode has its own instruction text. */
public enum OverlayMode {

    /** Several codes were found: click one (or drag a box). */
    PICK("Click a QR code to use it \u2014 or drag a box around one.   Esc or right-click to cancel."),

    /** Nothing was found on the whole screen: drag a box. */
    DRAW("No QR code found. Drag a box around the QR code.   Esc or right-click to cancel."),

    /** A drawn box contained no QR code: try again with a new box. */
    DRAW_AGAIN("No QR code in that area. Drag a new box to try again.   Esc or right-click to cancel.");

    private final String instruction;

    OverlayMode(String instruction) {
        this.instruction = instruction;
    }

    public String instruction() {
        return instruction;
    }
}