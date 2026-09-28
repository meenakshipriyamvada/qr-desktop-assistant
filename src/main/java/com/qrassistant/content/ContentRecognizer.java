package com.qrassistant.content;

import com.qrassistant.content.parsers.EmailParser;
import com.qrassistant.content.parsers.EventParser;
import com.qrassistant.content.parsers.MeCardParser;
import com.qrassistant.content.parsers.OtpAuthParser;
import com.qrassistant.content.parsers.PhoneParser;
import com.qrassistant.content.parsers.UnreadableParser;
import com.qrassistant.content.parsers.UrlParser;
import com.qrassistant.content.parsers.VCardParser;
import com.qrassistant.content.parsers.WifiParser;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Works out what a QR code's text is, by asking each parser in turn.
 * The FIRST parser that recognizes the text wins; if none does, it's plain text.
 *
 * Deterministic: the same text always gives the same result. No AI, no network.
 */
public class ContentRecognizer {

    private static final Logger LOG = Logger.getLogger(ContentRecognizer.class.getName());

    private final List<ContentParser> parsers;

    /** The standard parsers, in the order they're tried. ORDER MATTERS - see the comments. */
    public ContentRecognizer() {
        this(List.of(
                new UnreadableParser(),   // first: garbage shouldn't be mistaken for anything else
                new UrlParser(),
                // Formats with a clear prefix (WIFI:, BEGIN:VCARD, ...) come before the "guessing"
                // checks below, so e.g. an email address INSIDE a contact card can't win.
                new WifiParser(),
                new VCardParser(),
                new MeCardParser(),
                new EventParser(),
                new OtpAuthParser(),      // 2FA secrets -> sensitive text
                new PhoneParser(),
                new EmailParser()         // last: it also accepts a bare address
        ));
    }

    /** For tests, or to try a different set of parsers. */
    public ContentRecognizer(List<ContentParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public QrContent recognize(String text) {
        for (ContentParser parser : parsers) {
            try {
                Optional<QrContent> result = parser.parse(text);
                if (result.isPresent()) {
                    return result.get();
                }
            } catch (RuntimeException e) {
                // A parser bug must not break scanning: skip it and try the next one.
                // Log the error type only, never the text. (X8)
                LOG.warning(parser.getClass().getSimpleName() + " failed: " + e.getClass().getName());
            }
        }
        return new TextContent(text, false);   // readable, but nothing special
    }
}