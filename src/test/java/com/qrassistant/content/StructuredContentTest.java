package com.qrassistant.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the structured formats added in step 7b: Wi-Fi, contacts, events and 2FA secrets.
 */
class StructuredContentTest {

    private final ContentRecognizer recognizer = new ContentRecognizer();

    // --- Wi-Fi ---

    @Test
    void wifiBasic() {
        WifiContent wifi = assertInstanceOf(WifiContent.class,
                recognizer.recognize("WIFI:S:HomeNet;T:WPA;P:secret123;;"));
        assertEquals("HomeNet", wifi.networkName());
        assertEquals("WPA", wifi.security());
        assertEquals("secret123", wifi.password());
        assertFalse(wifi.hidden());
    }

    @Test
    void wifiWithEscapedCharactersAndHiddenNetwork() {
        WifiContent wifi = assertInstanceOf(WifiContent.class,
                recognizer.recognize("WIFI:S:My\\;Net;T:WPA;P:pa\\:ss\\\\word;H:true;;"));
        assertEquals("My;Net", wifi.networkName());
        assertEquals("pa:ss\\word", wifi.password());
        assertTrue(wifi.hidden());
    }

    @Test
    void openWifiNetwork() {
        WifiContent wifi = assertInstanceOf(WifiContent.class, recognizer.recognize("WIFI:S:Cafe;T:nopass;;"));
        assertEquals("nopass", wifi.security());
        assertEquals("", wifi.password());
    }

    @Test
    void wifiWithoutNetworkNameIsText() {
        assertInstanceOf(TextContent.class, recognizer.recognize("WIFI:T:WPA;P:secret;;"));
    }

    @Test
    void wifiPasswordThatLooksLikeEmailIsStillWifi() {
        // Order check: the email parser must not grab this just because it contains an @.
        assertInstanceOf(WifiContent.class, recognizer.recognize("WIFI:S:Net;T:WPA;P:me@home.com;;"));
    }

    // --- vCard ---

    @Test
    void fullVCard() {
        ContactContent contact = assertInstanceOf(ContactContent.class, recognizer.recognize(
                "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nORG:Acme Inc;Sales\n"
                        + "TEL;TYPE=CELL:+911234567890\nTEL:+910000000000\n"
                        + "EMAIL:john@acme.com\nURL:https://acme.com/team\nEND:VCARD"));
        assertEquals("John Doe", contact.name());
        assertEquals("Acme Inc", contact.organization());
        assertEquals(List.of("+911234567890", "+910000000000"), contact.phones());
        assertEquals(List.of("john@acme.com"), contact.emails());
        assertEquals(List.of("https://acme.com/team"), contact.websites());
    }

    @Test
    void vCardNameFromStructuredNameField() {
        ContactContent contact = assertInstanceOf(ContactContent.class,
                recognizer.recognize("BEGIN:VCARD\nN:Doe;Jane;;;\nEND:VCARD"));
        assertEquals("Jane Doe", contact.name());
    }

    @Test
    void vCardFoldedLineAndEscapes() {
        ContactContent contact = assertInstanceOf(ContactContent.class,
                recognizer.recognize("BEGIN:VCARD\r\nFN:Doe\\, John the\r\n  Third\r\nEND:VCARD"));
        assertEquals("Doe, John the Third", contact.name());
    }

    // --- MeCard ---

    @Test
    void meCardWithSeveralPhones() {
        ContactContent contact = assertInstanceOf(ContactContent.class,
                recognizer.recognize("MECARD:N:Doe,John;ORG:Acme;TEL:111;TEL:222;EMAIL:j@acme.com;;"));
        assertEquals("John Doe", contact.name());
        assertEquals("Acme", contact.organization());
        assertEquals(List.of("111", "222"), contact.phones());
        assertEquals(List.of("j@acme.com"), contact.emails());
    }

    // --- Events ---

    @Test
    void simpleEvent() {
        EventContent event = assertInstanceOf(EventContent.class, recognizer.recognize(
                "BEGIN:VEVENT\nSUMMARY:Team meeting\nDTSTART:20261001T090000Z\n"
                        + "DTEND:20261001T100000Z\nLOCATION:Room 4\nEND:VEVENT"));
        assertEquals("Team meeting", event.title());
        assertEquals("20261001T090000Z", event.start());
        assertEquals("20261001T100000Z", event.end());
        assertEquals("Room 4", event.location());
    }

    @Test
    void eventInsideCalendarWithTimeZone() {
        EventContent event = assertInstanceOf(EventContent.class, recognizer.recognize(
                "BEGIN:VCALENDAR\nPRODID:-//Example//EN\nSUMMARY:not the event\nBEGIN:VEVENT\n"
                        + "SUMMARY:Lecture\nDTSTART;TZID=Asia/Kolkata:20261001T090000\n"
                        + "DESCRIPTION:Bring\\nnotes\nEND:VEVENT\nEND:VCALENDAR"));
        assertEquals("Lecture", event.title());               // not the calendar-level SUMMARY
        assertEquals("20261001T090000", event.start());       // ";TZID=..." part removed
        assertEquals("Bring\nnotes", event.description());    // escaped n became a real line break
    }

    // --- 2FA secrets ---

    @Test
    void otpauthIsSensitiveText() {
        TextContent text = assertInstanceOf(TextContent.class,
                recognizer.recognize("otpauth://totp/Example:me@x.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"));
        assertTrue(text.sensitive());
    }

    @Test
    void authenticatorExportIsSensitiveText() {
        TextContent text = assertInstanceOf(TextContent.class,
                recognizer.recognize("otpauth-migration://offline?data=CjEKCkhlbGxvId6tvu8"));
        assertTrue(text.sensitive());
    }

    @Test
    void normalTextIsNotSensitive() {
        TextContent text = assertInstanceOf(TextContent.class, recognizer.recognize("hello world"));
        assertFalse(text.sensitive());
    }
}