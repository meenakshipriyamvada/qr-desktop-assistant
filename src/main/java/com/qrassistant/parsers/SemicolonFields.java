package com.qrassistant.content.parsers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the "KEY:value;KEY:value;;" style used by several QR formats:
 *   MATMSG:TO:a@b.com;SUB:Hello;BODY:Hi there;;
 *   WIFI:S:MyNetwork;T:WPA;P:secret;;
 *   MECARD:N:Doe,John;TEL:12345;TEL:67890;;      (a key can appear more than once)
 *
 * A backslash "escapes" the next character, so values can contain ; : , or a backslash themselves.
 */
final class SemicolonFields {

    private SemicolonFields() {
    }

    /**
     * @param fields the part AFTER the format prefix, e.g. "TO:a@b.com;SUB:Hello;;"
     * @return keys in UPPER case -> every value given for that key, in order
     */
    static Map<String, List<String>> parse(String fields) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < fields.length(); i++) {
            char c = fields.charAt(i);
            if (escaped) {
                current.append(c);   // the character after a backslash is always taken literally
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == ';') {
                addField(result, current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addField(result, current.toString());   // last field, if there was no final ';'
        return result;
    }

    /** The first value for a key, or "" if the key isn't there. */
    static String first(Map<String, List<String>> fields, String key) {
        List<String> values = fields.get(key);
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    /** All values for a key (possibly none). */
    static List<String> all(Map<String, List<String>> fields, String key) {
        return fields.getOrDefault(key, List.of());
    }

    /** "KEY:value" -> map entry. Pieces without a ':' (like the empty piece from ";;") are ignored. */
    private static void addField(Map<String, List<String>> result, String field) {
        int colon = field.indexOf(':');
        if (colon <= 0) {
            return;
        }
        String key = field.substring(0, colon).strip().toUpperCase(Locale.ROOT);
        String value = field.substring(colon + 1);
        result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }
}