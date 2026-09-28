package com.qrassistant.content.parsers;

import com.qrassistant.content.ContentParser;
import com.qrassistant.content.QrContent;
import com.qrassistant.content.UnknownContent;

import java.util.Optional;

/**
 * Catches content that isn't readable text: empty, mostly invisible control characters
 * (binary data), or full of the replacement character U+FFFD (text decoded with the wrong encoding).
 */
public class UnreadableParser implements ContentParser {

    /** If more than this share of the characters are unreadable, the whole thing counts as unreadable. */
    private static final double MAX_UNREADABLE_SHARE = 0.10;

    @Override
    public Optional<QrContent> parse(String text) {
        if (text.isBlank()) {
            return Optional.of(new UnknownContent(text));
        }
        long unreadable = text.chars().filter(UnreadableParser::isUnreadable).count();
        if (unreadable > text.length() * MAX_UNREADABLE_SHARE) {
            return Optional.of(new UnknownContent(text));
        }
        return Optional.empty();
    }

    private static boolean isUnreadable(int c) {
        if (c == '\n' || c == '\r' || c == '\t') {
            return false;   // normal line breaks and tabs are fine
        }
        return Character.getType(c) == Character.CONTROL || c == '\uFFFD';
    }
}