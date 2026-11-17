package com.qrassistant.capture;

/**
 * An image file couldn't be used. The message is written for the USER, in the app's own words:
 * it never contains the file's contents or a decoder's own error text.
 */
public class ImageLoadException extends Exception {

    public ImageLoadException(String userMessage) {
        super(userMessage);
    }
}
