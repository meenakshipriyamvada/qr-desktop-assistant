package com.qrassistant.content.parsers;

import com.qrassistant.content.ContentParser;
import com.qrassistant.content.PhoneContent;
import com.qrassistant.content.QrContent;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognizes "tel:" phone numbers, e.g. "tel:+91 98765 43210".
 *
 * A bare number like "9876543210" is NOT treated as a phone number: it could just as well
 * be an order number or a code, so it stays plain text.
 */
public class PhoneParser implements ContentParser {

    private static final String PREFIX = "tel:";

    /** Digits, spaces and the usual phone punctuation, with an optional + at the start. */
    private static final Pattern PHONE_CHARACTERS = Pattern.compile("\\+?[0-9 ().\\-]+");

    /** Fewer digits than this is almost certainly not a real phone number. */
    private static final int MIN_DIGITS = 3;

    @Override
    public Optional<QrContent> parse(String text) {
        String trimmed = text.strip();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            return Optional.empty();
        }
        String number = trimmed.substring(PREFIX.length()).strip();
        long digits = number.chars().filter(Character::isDigit).count();
        if (!PHONE_CHARACTERS.matcher(number).matches() || digits < MIN_DIGITS) {
            return Optional.empty();   // "tel:" followed by nonsense -> let it be plain text
        }
        return Optional.of(new PhoneContent(text, number));
    }
}