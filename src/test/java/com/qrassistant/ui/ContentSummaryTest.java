package com.qrassistant.ui;

import com.qrassistant.content.ContentRecognizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one-line summaries in the "several codes" list: short, sanitized, and never a secret. */
class ContentSummaryTest {

    private final ContentRecognizer recognizer = new ContentRecognizer();

    private String summary(String qrText) {
        return ContentLabels.summary(recognizer.recognize(qrText));
    }

    @Test
    void urlShowsTheAddress() {
        assertEquals("Web address: https://example.com", summary("https://example.com"));
    }

    @Test
    void wifiShowsTheNameButNeverThePassword() {
        String s = summary("WIFI:T:WPA;S:HomeNet;P:hunter2;;");
        assertEquals("Wi-Fi network: HomeNet", s);
        assertFalse(s.contains("hunter2"));
    }

    @Test
    void twoFactorSecretIsHidden() {
        String s = summary("otpauth://totp/Example:me?secret=JBSWY3DPEHPK3PXP");
        assertFalse(s.contains("JBSWY3DPEHPK3PXP"));
        assertTrue(s.contains("(hidden)"));
    }

    @Test
    void longTextIsShortened() {
        String s = summary("x".repeat(500));
        assertTrue(s.endsWith("…"));
        assertEquals("Text: ".length() + ContentLabels.SUMMARY_LENGTH + 1, s.length());
    }

    @Test
    void hiddenCharactersAreRevealed() {
        String s = summary("pay​pal");
        assertTrue(s.contains("⟦U+200B⟧"));
    }
}
