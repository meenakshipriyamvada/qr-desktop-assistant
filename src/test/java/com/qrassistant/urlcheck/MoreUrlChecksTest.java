package com.qrassistant.urlcheck;

import com.qrassistant.urlcheck.Indicator.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the step 9b checks: international names, host shape, short links, ports,
 * login tokens and length. Foreign letters are written as backslash-u codes (e.g. U+0430) so you can see them.
 */
class MoreUrlChecksTest {

    private final UrlSafetyChecker checker = new UrlSafetyChecker();

    private List<Level> levels(String url) {
        return checker.check(url).indicators().stream().map(Indicator::level).toList();
    }

    private boolean anyMessageContains(String url, String text) {
        return checker.check(url).indicators().stream().anyMatch(i -> i.message().contains(text));
    }

    // --- International names ---

    @Test
    void lookalikeWithOneCyrillicLetterIsDanger() {
        // the first letter is a Cyrillic "a", the rest English
        List<Level> found = levels("https://\u0430pple.com/");
        assertTrue(found.contains(Level.DANGER));
        assertTrue(anyMessageContains("https://\u0430pple.com/", "xn--"));   // the real name is shown
    }

    @Test
    void sameTrickInPunycodeFormIsCaughtToo() {
        assertTrue(levels("https://xn--pple-43d.com/").contains(Level.DANGER));
    }

    @Test
    void allCyrillicLookalikeIsDanger() {
        // looks like "apple" but every letter is Cyrillic
        assertTrue(levels("https://\u0430\u0440\u0440\u04CF\u0435.com/").contains(Level.DANGER));
    }

    @Test
    void genuineInternationalNamesAreOnlyWarned() {
        assertEquals(List.of(Level.WARN, Level.OK), levels("https://b\u00FCcher.de/"));
        // a Hindi name with the Hindi ending for India
        String hindi = "https://\u0909\u0926\u093E\u0939\u0930\u0923.\u092D\u093E\u0930\u0924/";
        assertEquals(List.of(Level.WARN, Level.OK), levels(hindi));
    }

    // --- Host shape ---

    @Test
    void nameWithoutDotIsWarned() {
        assertTrue(levels("http://intranet/").contains(Level.WARN));
        assertTrue(anyMessageContains("http://intranet/", "not a normal public website name"));
    }

    @Test
    void unusualCharactersAreWarned() {
        assertTrue(anyMessageContains("https://my_site.example.com/", "unusual characters"));
    }

    @Test
    void manyPartsRemindToReadFromTheRight() {
        assertTrue(anyMessageContains("https://paypal.com.secure.login.example.ru/", "from the RIGHT"));
    }

    // --- Short links ---

    @Test
    void shortLinkIsNoted() {
        assertTrue(anyMessageContains("https://bit.ly/3abcXYZ", "short link"));
        assertTrue(anyMessageContains("https://www.tinyurl.com/abc", "short link"));
    }

    // --- Ports ---

    @Test
    void unusualPortIsNoted() {
        assertTrue(anyMessageContains("https://example.com:8443/", "8443"));
    }

    @Test
    void normalPortWrittenOutIsNotNoted() {
        assertEquals(List.of(Level.OK), levels("https://example.com:443/"));
    }

    // --- Login tokens ---

    @Test
    void loginTokenInLinkIsWarned() {
        assertTrue(anyMessageContains("https://example.com/reset?token=abc123&user=me", "token"));
    }

    @Test
    void harmlessParametersAreNotWarned() {
        assertEquals(List.of(Level.OK), levels("https://example.com/page?utm_source=poster&lang=en"));
    }

    // --- Length ---

    @Test
    void longAddressIsNoted() {
        String longUrl = "https://example.com/" + "a".repeat(250);
        assertTrue(levels(longUrl).contains(Level.INFO));
    }

    // --- Nothing extra for a plain, normal site ---

    @Test
    void normalSiteStillOnlyHasTheHttpsFact() {
        assertEquals(List.of(Level.OK), levels("https://www.example.com/about"));
        assertFalse(levels("https://github.com/someone/project").contains(Level.WARN));
    }
}