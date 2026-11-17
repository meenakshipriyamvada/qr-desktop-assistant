package com.qrassistant.content.parsers;

import com.qrassistant.content.ContentParser;
import com.qrassistant.content.QrContent;
import com.qrassistant.content.UrlContent;

import java.util.Locale;
import java.util.Optional;

/**
 * Recognizes web addresses: text starting with http:// or https://.
 *
 * Deliberately strict:
 *  - no scheme ("example.com", "www.example.com") -> not a URL; it stays plain text (U3)
 *  - other schemes (javascript:, file:, ...) -> not a URL (U1)
 *  - spaces or line breaks inside -> not a URL (real URLs never contain them)
 */
public class UrlParser implements ContentParser {

    @Override
    public Optional<QrContent> parse(String text) {
        String trimmed = text.strip();
        // QR codes often store URLs in CAPITALS (it makes the code smaller), so ignore case.
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean webScheme = lower.startsWith("http://") || lower.startsWith("https://");
        if (!webScheme || containsWhitespace(trimmed)) {
            return Optional.empty();
        }
        return Optional.of(new UrlContent(text, trimmed));
    }

    private static boolean containsWhitespace(String s) {
        return s.chars().anyMatch(Character::isWhitespace);
    }
}