package com.qrassistant.content.parsers;

import com.qrassistant.content.ContentParser;
import com.qrassistant.content.EventContent;
import com.qrassistant.content.QrContent;

import java.util.Locale;
import java.util.Optional;

/**
 * Recognizes calendar events (iCalendar):
 *   BEGIN:VEVENT
 *   SUMMARY:Team meeting
 *   DTSTART:20261001T090000Z
 *   DTEND:20261001T100000Z
 *   LOCATION:Room 4
 *   END:VEVENT
 * Often wrapped in BEGIN:VCALENDAR ... END:VCALENDAR, which is fine too.
 */
public class EventParser implements ContentParser {

    @Override
    public Optional<QrContent> parse(String text) {
        String upper = text.strip().toUpperCase(Locale.ROOT);
        boolean isCalendar = upper.startsWith("BEGIN:VEVENT")
                || (upper.startsWith("BEGIN:VCALENDAR") && upper.contains("BEGIN:VEVENT"));
        if (!isCalendar) {
            return Optional.empty();
        }

        String title = "";
        String start = "";
        String end = "";
        String location = "";
        String description = "";
        boolean insideEvent = false;

        for (com.qrassistant.content.parsers.ContentLines.Property p : com.qrassistant.content.parsers.ContentLines.parse(text)) {
            String value = p.value().strip();
            if (p.name().equals("BEGIN") && value.equalsIgnoreCase("VEVENT")) {
                insideEvent = true;
                continue;
            }
            if (p.name().equals("END") && value.equalsIgnoreCase("VEVENT")) {
                break;   // only the FIRST event is read
            }
            if (!insideEvent) {
                continue;   // skip calendar-level lines before the event
            }
            switch (p.name()) {
                case "SUMMARY" -> title = title.isEmpty() ? value : title;
                case "DTSTART" -> start = start.isEmpty() ? value : start;
                case "DTEND" -> end = end.isEmpty() ? value : end;
                case "LOCATION" -> location = location.isEmpty() ? value : location;
                case "DESCRIPTION" -> description = description.isEmpty() ? value : description;
                default -> { /* other fields are ignored */ }
            }
        }
        return Optional.of(new EventContent(text, title, start, end, location, description));
    }
}