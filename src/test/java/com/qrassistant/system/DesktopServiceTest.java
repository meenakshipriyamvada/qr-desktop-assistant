package com.qrassistant.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lock #2: only http/https addresses with a host may ever reach the browser.
 * These tests never open anything: they only call the check.
 */
class DesktopServiceTest {

    @Test
    void webAddressesAreAllowed() {
        assertTrue(DesktopService.webAddress("https://example.com/page?q=1").isPresent());
        assertTrue(DesktopService.webAddress("http://example.com").isPresent());
        assertTrue(DesktopService.webAddress("HTTPS://EXAMPLE.COM").isPresent());   // QR codes often use capitals
    }

    @Test
    void otherSchemesAreRefused() {
        assertTrue(DesktopService.webAddress("javascript:alert(1)").isEmpty());
        assertTrue(DesktopService.webAddress("file:///C:/Windows/System32/calc.exe").isEmpty());
        assertTrue(DesktopService.webAddress("ms-settings:privacy").isEmpty());
        assertTrue(DesktopService.webAddress("mailto:a@b.com").isEmpty());
        assertTrue(DesktopService.webAddress("ftp://example.com").isEmpty());
    }

    @Test
    void addressesWithoutHostAreRefused() {
        assertTrue(DesktopService.webAddress("https://").isEmpty());
        assertTrue(DesktopService.webAddress("https:example.com").isEmpty());
        assertTrue(DesktopService.webAddress("example.com").isEmpty());
        assertTrue(DesktopService.webAddress("").isEmpty());
    }

    @Test
    void brokenAddressesAreRefused() {
        assertTrue(DesktopService.webAddress("https://exa mple.com").isEmpty());
        assertTrue(DesktopService.webAddress("https://example.com/\npath").isEmpty());
    }

    @Test
    void emailRefusesEverythingButMailto() {
        DesktopService desktop = new DesktopService();
        assertEquals(DesktopService.Result.REFUSED,
                desktop.composeEmail(java.net.URI.create("https://example.com")));
        assertEquals(DesktopService.Result.REFUSED,
                desktop.composeEmail(java.net.URI.create("file:///C:/x.txt")));
    }
}
