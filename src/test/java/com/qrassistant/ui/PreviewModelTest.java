package com.qrassistant.ui;

import com.qrassistant.content.ContentRecognizer;
import com.qrassistant.content.QrContent;
import com.qrassistant.content.UrlContent;
import com.qrassistant.urlcheck.UrlReport;
import com.qrassistant.urlcheck.UrlSafetyChecker;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the preview SHOWS, without opening a window: masking, the real-site line,
 * hidden characters, and honest wording.
 */
class PreviewModelTest {

    private final ContentRecognizer recognizer = new ContentRecognizer();
    private final UrlSafetyChecker checker = new UrlSafetyChecker();

    private PreviewModel model(String qrText) {
        QrContent content = recognizer.recognize(qrText);
        UrlReport report = content instanceof UrlContent url ? checker.check(url.url()) : null;
        return PreviewModel.build(content, report, List.of(), true, ZoneId.of("Asia/Kolkata"));
    }

    private static Optional<PreviewDetails.Row> row(PreviewModel model, String label) {
        return model.rows().stream().filter(r -> r.label().equals(label)).findFirst();
    }

    // --- Secrets ---

    @Test
    void wifiPasswordIsMasked() {
        PreviewDetails.Row password = row(model("WIFI:T:WPA;S:HomeNet;P:hunter2;;"), "Password").orElseThrow();
        assertTrue(password.secret());
        assertEquals(PreviewDetails.MASK, password.maskedValue());
        assertEquals("hunter2", password.value());           // shown only after "Show"
        assertFalse(password.toString().contains("hunter2")); // never leaks into a log (X8)
    }

    @Test
    void otpSecretIsMaskedButIssuerStaysVisible() {
        PreviewDetails.Row content = row(model(
                "otpauth://totp/Example:me@mail.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"), "Content").orElseThrow();
        assertFalse(content.maskedValue().contains("JBSWY3DPEHPK3PXP"));
        assertTrue(content.maskedValue().contains("Example:me@mail.com"));
        assertTrue(content.maskedValue().contains("issuer=Example"));
    }

    @Test
    void otpExportWithoutSecretFieldIsFullyMasked() {
        PreviewDetails.Row content = row(model("otpauth-migration://offline?payload=ABC"), "Content").orElseThrow();
        assertEquals(PreviewDetails.MASK, content.maskedValue());
    }

    // --- URLs ---

    @Test
    void realSiteIsTheAsciiHostWithALooksLikeRow() {
        PreviewModel m = model("https://bücher.de/shop");
        assertEquals("Real site", m.headlineLabel());
        assertEquals("xn--bcher-kva.de", m.headline());
        assertEquals("bücher.de", row(m, "Looks like").orElseThrow().value());
    }

    @Test
    void plainAsciiSiteHasNoLooksLikeRow() {
        PreviewModel m = model("https://example.com");
        assertEquals("example.com", m.headline());
        assertTrue(row(m, "Looks like").isEmpty());
    }

    @Test
    void summaryNeverClaimsSafety() {
        assertTrue(model("https://example.com").checksSummary().startsWith("No problems found by basic checks"));
        assertTrue(model("http://example.com").checksSummary().startsWith("Basic checks found warning signs"));
    }

    @Test
    void openableUrlOffersTheRedirectCheck() {
        assertTrue(model("https://example.com").canCheckRedirects());
        assertFalse(model("https://").canCheckRedirects());
        assertFalse(model("Hello").canCheckRedirects());
    }

    // --- Hidden characters and other content ---

    @Test
    void hiddenCharactersAreFlaggedAndRevealed() {
        PreviewModel m = model("Pay to: Alice‮cilbup");
        assertTrue(m.hadHiddenCharacters());
        assertTrue(row(m, "Text").orElseThrow().value().contains("⟦U+202E⟧"));
    }

    @Test
    void normalTextHasNoWarning() {
        assertFalse(model("Hello there").hadHiddenCharacters());
    }

    @Test
    void eventTimesAreReadable() {
        PreviewModel m = model("BEGIN:VEVENT\nSUMMARY:Exam\nDTSTART:20261001T090000Z\nEND:VEVENT");
        assertEquals("Calendar event", m.kind());
        assertEquals("Thu 1 Oct 2026, 14:30 (your time)", row(m, "Starts").orElseThrow().value());
    }
}
