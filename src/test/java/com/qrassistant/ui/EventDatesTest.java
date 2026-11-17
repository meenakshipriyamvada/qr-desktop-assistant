package com.qrassistant.ui;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventDatesTest {

    /** A fixed zone, so the tests give the same answer on every computer. UTC+05:30. */
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Test
    void dateOnlyIsAllDay() {
        assertEquals("Thu 1 Oct 2026 (all day)", EventDates.describe("20261001", INDIA));
    }

    @Test
    void utcTimeIsConvertedToYourZone() {
        assertEquals("Thu 1 Oct 2026, 14:30 (your time)", EventDates.describe("20261001T090000Z", INDIA));
    }

    @Test
    void conversionCanMoveToTheNextDay() {
        assertEquals("Fri 1 Jan 2027, 01:30 (your time)", EventDates.describe("20261231T200000Z", INDIA));
    }

    @Test
    void timeWithoutZoneSaysSo() {
        assertEquals("Thu 1 Oct 2026, 09:00 (time zone unknown)",
                EventDates.describe("20261001T090000", INDIA));
    }

    @Test
    void impossibleDatesAreShownAsWritten() {
        assertEquals("20260230", EventDates.describe("20260230", INDIA));                   // 30 February
        assertEquals("20261001T250000Z", EventDates.describe("20261001T250000Z", INDIA));   // hour 25
    }

    @Test
    void unknownFormatsAreShownAsWritten() {
        assertEquals("next Tuesday", EventDates.describe("next Tuesday", INDIA));
        assertEquals("2026-10-01", EventDates.describe("2026-10-01", INDIA));
        assertEquals("", EventDates.describe("", INDIA));
    }
}
