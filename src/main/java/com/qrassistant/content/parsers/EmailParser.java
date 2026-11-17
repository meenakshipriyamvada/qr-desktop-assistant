package com.qrassistant.content.parsers;

import com.qrassistant.content.ContentParser;
import com.qrassistant.content.EmailContent;
import com.qrassistant.content.QrContent;
import java.util.List;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognizes emails in three common forms:
 *   mailto:someone@example.com?subject=Hi&body=Hello%20there
 *   MATMSG:TO:someone@example.com;SUB:Hi;BODY:Hello there;;
 *   someone@example.com                (just an address)
 */
public class EmailParser implements ContentParser {

    private static final String MAILTO = "mailto:";
    private static final String MATMSG = "matmsg:";

    /**
     * A simple "looks like an email address" check: something@something.something,
     * with no spaces and none of : ; , (so "WIFI:S:x;P:a@b.c;;" can never look like an address).
     */
    private static final Pattern PLAIN_ADDRESS = Pattern.compile("[^\\s@:;,]+@[^\\s@:;,]+\\.[^\\s@:;,]+");

    @Override
    public Optional<QrContent> parse(String text) {
        String trimmed = text.strip();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.startsWith(MAILTO)) {
            return Optional.of(parseMailto(text, trimmed.substring(MAILTO.length())));
        }
        if (lower.startsWith(MATMSG)) {
            Map<String, List<String>> fields = SemicolonFields.parse(trimmed.substring(MATMSG.length()));
            return Optional.of(new EmailContent(text,
                    SemicolonFields.first(fields, "TO").strip(),
                    SemicolonFields.first(fields, "SUB"),
                    SemicolonFields.first(fields, "BODY")));
        }
        if (PLAIN_ADDRESS.matcher(trimmed).matches()) {
            return Optional.of(new EmailContent(text, trimmed, "", ""));
        }
        return Optional.empty();
    }

    /** "someone@x.com?subject=Hi&body=Hello" -> address, subject, body. */
    private static EmailContent parseMailto(String rawText, String afterPrefix) {
        String address = afterPrefix;
        String subject = "";
        String body = "";

        int questionMark = afterPrefix.indexOf('?');
        if (questionMark >= 0) {
            address = afterPrefix.substring(0, questionMark);
            for (String pair : afterPrefix.substring(questionMark + 1).split("&")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String key = pair.substring(0, equals).toLowerCase(Locale.ROOT);
                String value = decode(pair.substring(equals + 1));
                if (key.equals("subject") && subject.isEmpty()) {
                    subject = value;
                } else if (key.equals("body") && body.isEmpty()) {
                    body = value;
                }
            }
        }
        return new EmailContent(rawText, decode(address).strip(), subject, body);
    }

    /**
     * Turns "Hello%20there" into "Hello there".
     * URLDecoder is meant for web forms, where "+" means a space. In mailto links "+" is a real plus
     * (think name+tag@gmail.com), so we protect it first by turning it into its code, %2B.
     */
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;   // broken % code (e.g. "%zz"): show it as it was rather than fail
        }
    }
}