package com.qrassistant.urlcheck;

import com.qrassistant.urlcheck.Indicator.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the URL safety checks (step 9a).
 * Each test checks which LEVELS of findings come out - and that no "safe" verdict exists.
 */
class UrlSafetyCheckerTest {

    private final UrlSafetyChecker checker = new UrlSafetyChecker();

    /** The levels found, most serious first, e.g. [DANGER, WARN]. */
    private List<Level> levels(String url) {
        return checker.check(url).indicators().stream().map(Indicator::level).toList();
    }

    // --- The basics ---

    @Test
    void normalHttpsSiteHasOnlyTheHttpsFact() {
        UrlReport report = checker.check("https://example.com/page");
        assertEquals(List.of(Level.OK), levels("https://example.com/page"));
        assertTrue(report.canOpen());
        assertEquals("example.com", report.parsed().orElseThrow().host());
    }

    @Test
    void plainHttpIsWarned() {
        assertEquals(List.of(Level.WARN), levels("http://example.com"));
        assertTrue(checker.check("http://example.com").canOpen());   // allowed, with the warning (Q9)
    }

    // --- The @ trick ---

    @Test
    void atTrickShowsTheRealSite() {
        UrlReport report = checker.check("https://google.com@evil.com/login");
        assertEquals("evil.com", report.parsed().orElseThrow().host());
        assertEquals(Level.DANGER, report.indicators().get(0).level());
        assertTrue(report.indicators().get(0).message().contains("evil.com"));
    }

    // --- IP addresses ---

    @Test
    void publicIpAddressIsWarned() {
        assertEquals(List.of(Level.WARN, Level.OK), levels("https://8.8.8.8/"));
    }

    @Test
    void homeRouterAddressIsIpAndLocal() {
        // three warnings: IP address + local network + not encrypted (http)
        assertEquals(List.of(Level.WARN, Level.WARN, Level.WARN), levels("http://192.168.1.1/admin"));
    }

    @Test
    void addressAsOneBigNumberIsCaught() {
        // 3232235777 is 192.168.1.1 written as a single number
        UrlReport report = checker.check("http://3232235777/");
        assertEquals(Level.DANGER, report.indicators().get(0).level());
        assertTrue(report.indicators().get(0).message().contains("192.168.1.1"));
        assertTrue(levels("http://3232235777/").contains(Level.WARN));   // ...and it's local
    }

    @Test
    void hexAndShortFormsAreCaught() {
        assertTrue(levels("http://0x7f.1/").contains(Level.DANGER));       // 127.0.0.1
        assertTrue(levels("http://127.1/").contains(Level.DANGER));        // 127.0.0.1
        assertTrue(levels("http://0300.0250.1.1/").contains(Level.DANGER)); // octal 192.168.1.1
    }

    @Test
    void normalNamesWithNumbersAreNotIpAddresses() {
        assertEquals(List.of(Level.OK), levels("https://1password.com"));
        assertEquals(List.of(Level.OK), levels("https://web3.example.com"));
    }

    // --- Local network ---

    @Test
    void localhostIsLocal() {
        // two warnings: local + not encrypted (http)
        // two warnings: local + not encrypted (http); plus a note about the unusual port (step 9b)
        assertEquals(List.of(Level.WARN, Level.WARN, Level.INFO), levels("http://localhost:3000/"));
    }

    @Test
    void privateAddressRanges() {
        assertTrue(levels("https://10.0.0.5").size() >= 2);
        assertTrue(levels("https://172.20.1.1").size() >= 2);
        assertEquals(List.of(Level.WARN, Level.OK), levels("https://172.32.0.1"));   // just outside 172.16/12
        assertTrue(levels("http://169.254.169.254/latest").size() >= 2);
    }

    @Test
    void ipv6LoopbackWithPort() {
        UrlReport report = checker.check("http://[::1]:8080/");
        assertEquals(8080, report.parsed().orElseThrow().port());
        // three warnings: IP address + local + not encrypted (http)
        // three warnings: IP address + local + not encrypted (http); plus the unusual port (step 9b)
        assertEquals(List.of(Level.WARN, Level.WARN, Level.WARN, Level.INFO), levels("http://[::1]:8080/"));
    }

    // --- Unreadable URLs can't be opened ---

    @Test
    void unreadableUrlsCannotBeOpened() {
        for (String bad : List.of(
                "https://exa mple.com",            // space
                "https://evil.com\\@good.com",       // backslash trick
                "https://example.com:99999/",      // port out of range
                "https://",                        // no host
                "http://[::1/",                    // broken IPv6
                "https://a..com/")) {              // empty part in the name
            UrlReport report = checker.check(bad);
            assertFalse(report.canOpen(), bad);
            assertEquals(List.of(Level.DANGER), levels(bad), bad);
        }
    }

    // --- International names are read, not rejected (step 9b adds the warning) ---

    @Test
    void internationalNameIsConverted() {
        ParsedUrl parsed = checker.check("https://b\u00FCcher.de/").parsed().orElseThrow();
        assertEquals("xn--bcher-kva.de", parsed.host());
        assertEquals("b\u00FCcher.de", parsed.hostUnicode());
    }

    // --- Robustness ---

    @Test
    void brokenCheckDoesNotHideTheOthers() {
        UrlCheck broken = url -> {
            throw new IllegalStateException("bug");
        };
        UrlSafetyChecker withBroken = new UrlSafetyChecker(List.of(broken, new com.qrassistant.urlcheck.checks.SchemeCheck()));
        assertEquals(1, withBroken.check("https://example.com").indicators().size());
    }
}