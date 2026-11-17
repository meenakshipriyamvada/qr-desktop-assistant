package com.qrassistant.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * Turns calendar dates from a QR code into something a person can read.
 *
 *   "20261001"          -> "Thu 1 Oct 2026 (all day)"
 *   "20261001T090000Z"  -> "Thu 1 Oct 2026, 14:30 (your time)"     (Z = UTC, converted to your zone)
 *   "20261001T090000"   -> "Thu 1 Oct 2026, 09:00 (time zone unknown)"
 *
 * Anything else is returned exactly as written. The caller still runs the result through
 * DisplaySanitizer, because that fallback is untrusted QR text.
 *
 * Pure logic (no JavaFX), so it has automatic tests.
 */
public final class EventDates {

    // "uuuu" (not "yyyy") is needed with STRICT, which rejects impossible dates like 30 February.
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter SHOW_DATE =
            DateTimeFormatter.ofPattern("EEE d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter SHOW_DATE_TIME =
            DateTimeFormatter.ofPattern("EEE d MMM uuuu, HH:mm", Locale.ENGLISH);

    private EventDates() {
    }

    /**
     * @param value    the date as written in the QR code (DTSTART / DTEND value)
     * @param yourZone the user's time zone; UTC times are converted to it
     */
    public static String describe(String value, ZoneId yourZone) {
        String v = value.strip();
        try {
            if (v.length() == 8) {
                return LocalDate.parse(v, DATE).format(SHOW_DATE) + " (all day)";
            }
            if (v.length() == 16 && v.endsWith("Z")) {
                LocalDateTime utc = LocalDateTime.parse(v.substring(0, 15), DATE_TIME);
                return utc.atZone(ZoneOffset.UTC)
                        .withZoneSameInstant(yourZone)
                        .format(SHOW_DATE_TIME) + " (your time)";
            }
            if (v.length() == 15) {
                // The parser drops ";TZID=..." so we can't know the zone. Say so instead of guessing.
                return LocalDateTime.parse(v, DATE_TIME).format(SHOW_DATE_TIME) + " (time zone unknown)";
            }
        } catch (DateTimeParseException e) {
            // Not a real date: fall through and show it as written.
        }
        return value;
    }
}
