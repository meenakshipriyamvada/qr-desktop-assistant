package com.qrassistant.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for DisplaySanitizer. The hidden characters are written as backslash-u escapes (e.g. U+202E),
 * because typed directly they'd be... invisible.
 */
class DisplaySanitizerTest {

    @Test
    void normalTextIsUnchanged() {
        DisplaySanitizer.SanitizedText result = DisplaySanitizer.forSingleLine("https://example.com/page");
        assertEquals("https://example.com/page", result.text());
        assertFalse(result.hadHiddenCharacters());
    }

    @Test
    void rightToLeftOverrideIsRevealed() {
        DisplaySanitizer.SanitizedText result = DisplaySanitizer.forSingleLine("invoice\u202Efdp.exe");
        assertEquals("invoice\u27E6U+202E\u27E7fdp.exe", result.text());
        assertTrue(result.hadHiddenCharacters());
    }

    @Test
    void zeroWidthSpaceIsRevealed() {
        DisplaySanitizer.SanitizedText result = DisplaySanitizer.forSingleLine("pay\u200Bpal.com");
        assertEquals("pay\u27E6U+200B\u27E7pal.com", result.text());
        assertTrue(result.hadHiddenCharacters());
    }

    @Test
    void controlCharacterIsRevealed() {
        DisplaySanitizer.SanitizedText result = DisplaySanitizer.forSingleLine("ding\u0007");
        assertEquals("ding\u27E6U+0007\u27E7", result.text());
        assertTrue(result.hadHiddenCharacters());
    }

    @Test
    void byteOrderMarkIsRevealed() {
        assertTrue(DisplaySanitizer.forSingleLine("\uFEFFhello").hadHiddenCharacters());
    }

    @Test
    void invisibleTagCharacterIsRevealedAsOneMarker() {
        // U+E0041 is stored as TWO Java chars; it must still become ONE marker.
        String hidden = new String(Character.toChars(0xE0041));
        DisplaySanitizer.SanitizedText result = DisplaySanitizer.forSingleLine("a" + hidden + "b");
        assertEquals("a\u27E6U+E0041\u27E7b", result.text());
    }

    @Test
    void multiLineKeepsLineBreaksAndTabs() {
        DisplaySanitizer.SanitizedText result = DisplaySanitizer.forMultiLine("Line 1\r\nLine 2\n\tindented");
        assertEquals("Line 1\nLine 2\n\tindented", result.text());
        assertFalse(result.hadHiddenCharacters());
    }

    @Test
    void singleLineRevealsLineBreaks() {
        // A line break inside a one-line value could fake an extra line in the preview.
        DisplaySanitizer.SanitizedText result = DisplaySanitizer.forSingleLine("Net\nSecurity: OK");
        assertEquals("Net\u27E6U+000A\u27E7Security: OK", result.text());
        assertTrue(result.hadHiddenCharacters());
    }

    @Test
    void disguisedLineSeparatorIsRevealed() {
        assertTrue(DisplaySanitizer.forMultiLine("a\u2028b").hadHiddenCharacters());
    }

    @Test
    void indianScriptJoinersAreAllowed() {
        // Hindi "ksha" written with a zero-width joiner: legitimate, must not be flagged.
        String hindi = "\u0915\u094D\u200D\u0937";
        DisplaySanitizer.SanitizedText result = DisplaySanitizer.forSingleLine(hindi);
        assertEquals(hindi, result.text());
        assertFalse(result.hadHiddenCharacters());
    }

    @Test
    void emojiJoinersAreAllowed() {
        String family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67";   // man-woman-girl emoji
        assertFalse(DisplaySanitizer.forSingleLine(family).hadHiddenCharacters());
    }
}