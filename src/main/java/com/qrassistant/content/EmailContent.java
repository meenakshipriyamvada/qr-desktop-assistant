package com.qrassistant.content;

/**
 * An email to send.
 *
 * @param address who to send to (may be empty if the QR code left it out)
 * @param subject may be empty
 * @param body    may be empty
 */
public record EmailContent(String rawText, String address, String subject, String body) implements QrContent {
}