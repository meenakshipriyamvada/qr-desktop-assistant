package com.qrassistant.content;

/**
 * A phone number to call.
 *
 * @param number the number as written in the QR code, e.g. "+91 98765 43210"
 */
public record PhoneContent(String rawText, String number) implements QrContent {
}