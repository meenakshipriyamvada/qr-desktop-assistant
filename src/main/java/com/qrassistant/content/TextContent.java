package com.qrassistant.content;

/**
 * Readable text that isn't any of the special formats. The fallback for everything readable.
 *
 * @param sensitive true if the text looks like a secret, e.g. a 2FA setup code (otpauth://).
 *                  The preview hides sensitive text until the user asks to see it. (X4)
 */
public record TextContent(String rawText, boolean sensitive) implements QrContent {
}