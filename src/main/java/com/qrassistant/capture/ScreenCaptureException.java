package com.qrassistant.capture;

/**
 * Thrown when screen capture is not possible at all on this machine
 * (for example: Java is running "headless", or Windows refused access).
 *
 * The message is written for the user and never contains screen content.
 */
public class ScreenCaptureException extends Exception {

    private static final long serialVersionUID = 1L;

    public ScreenCaptureException(String message, Throwable cause) {
        super(message, cause);
    }
}