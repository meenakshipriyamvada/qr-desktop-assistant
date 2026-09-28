package com.qrassistant.content;

/**
 * A calendar event (iCalendar VEVENT).
 *
 * @param start as written in the QR code, e.g. "20261001T090000Z" (the preview formats it later)
 * @param end   same format; may be empty
 */
public record EventContent(String rawText, String title, String start, String end,
                           String location, String description) implements QrContent {
}