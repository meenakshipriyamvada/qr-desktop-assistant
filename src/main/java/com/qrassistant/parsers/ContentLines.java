package com.qrassistant.content.parsers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the line-based format shared by vCard (contacts) and iCalendar (events):
 *
 *   BEGIN:VCARD
 *   FN:John Doe
 *   TEL;TYPE=CELL:+91 98765 43210
 *   END:VCARD
 *
 * Each line is NAME[;parameters]:value. Two rules from the standards are handled here:
 *  - "folding": a long line may continue on the next line, which then starts with a space or tab
 *  - escapes in values: backslash-n = new line; backslash before , ; or a backslash = that character
 */
final class ContentLines {

    /** One line: e.g. name "TEL", value "+91 98765 43210". Parameters (TYPE=CELL) are dropped. */
    record Property(String name, String value) {
    }

    private ContentLines() {
    }

    static List<Property> parse(String text) {
        List<Property> properties = new ArrayList<>();
        for (String line : unfold(text)) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;   // not a NAME:value line - skip it
            }
            String nameAndParameters = line.substring(0, colon);
            String name = nameAndParameters.split(";", 2)[0];   // drop ";TYPE=CELL" etc.
            int dot = name.indexOf('.');
            if (dot >= 0) {
                name = name.substring(dot + 1);   // "item1.TEL" -> "TEL" (a grouping some phones add)
            }
            properties.add(new Property(name.strip().toUpperCase(Locale.ROOT),
                    unescape(line.substring(colon + 1))));
        }
        return properties;
    }

    /** Joins folded lines back together. */
    private static List<String> unfold(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            boolean continuation = !lines.isEmpty() && (line.startsWith(" ") || line.startsWith("\t"));
            if (continuation) {
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + line.substring(1));
            } else {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                result.append(next == 'n' || next == 'N' ? '\n' : next);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}