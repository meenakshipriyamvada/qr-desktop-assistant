package com.qrassistant.content.parsers;

import com.qrassistant.content.ContactContent;
import com.qrassistant.content.ContentParser;
import com.qrassistant.content.QrContent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recognizes MeCard contacts, a shorter one-line format popular for QR codes:
 *   MECARD:N:Doe,John;ORG:Acme;TEL:+911234567890;EMAIL:john@acme.com;URL:https://acme.com;;
 */
public class MeCardParser implements ContentParser {

    private static final String PREFIX = "mecard:";

    @Override
    public Optional<QrContent> parse(String text) {
        String trimmed = text.strip();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            return Optional.empty();
        }
        Map<String, List<String>> fields = com.qrassistant.content.parsers.SemicolonFields.parse(trimmed.substring(PREFIX.length()));
        return Optional.of(new ContactContent(text,
                readableName(com.qrassistant.content.parsers.SemicolonFields.first(fields, "N")),
                com.qrassistant.content.parsers.SemicolonFields.first(fields, "ORG").strip(),
                com.qrassistant.content.parsers.SemicolonFields.all(fields, "TEL"),
                com.qrassistant.content.parsers.SemicolonFields.all(fields, "EMAIL"),
                com.qrassistant.content.parsers.SemicolonFields.all(fields, "URL")));
    }

    /** MeCard names are "Last,First" -> "First Last". A name without a comma is kept as it is. */
    private static String readableName(String name) {
        int comma = name.indexOf(',');
        if (comma < 0) {
            return name.strip();
        }
        return (name.substring(comma + 1).strip() + " " + name.substring(0, comma).strip()).strip();
    }
}