package com.qrassistant.action;

import com.qrassistant.content.ContentRecognizer;
import com.qrassistant.content.QrContent;
import com.qrassistant.content.UrlContent;
import com.qrassistant.system.ClipboardService;
import com.qrassistant.system.DesktopService;
import com.qrassistant.urlcheck.UrlReport;
import com.qrassistant.urlcheck.UrlSafetyChecker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which buttons are offered for which content. Nothing is run here,
 * so no clipboard, browser or JavaFX is needed.
 */
class ActionManagerTest {

    private final ContentRecognizer recognizer = new ContentRecognizer();
    private final UrlSafetyChecker checker = new UrlSafetyChecker();
    private final ActionManager manager = new ActionManager(new ClipboardService(), new DesktopService());

    /** The button labels for a QR text, going through the same steps as the real workflow. */
    private List<String> labels(String qrText) {
        return actions(qrText).stream().map(Action::label).toList();
    }

    private List<Action> actions(String qrText) {
        QrContent content = recognizer.recognize(qrText);
        UrlReport report = content instanceof UrlContent url ? checker.check(url.url()) : null;
        return manager.actionsFor(content, report);
    }

    @Test
    void webAddressCanBeOpenedOrCopied() {
        assertEquals(List.of("Open in browser", "Copy address"), labels("https://example.com"));
        assertEquals(List.of("Open in browser", "Copy address"), labels("http://example.com"));   // allowed, warned
    }

    @Test
    void unreadableAddressIsNotOffered() {
        // Starts with https:// so it's a UrlContent, but it has no host: lock #1 says no.
        assertEquals(List.of("Copy address"), labels("https://"));
    }

    @Test
    void noAddressMeansNoReport() {
        // Defensive: without a report, never offer Open.
        QrContent content = recognizer.recognize("https://example.com");
        List<String> labels = manager.actionsFor(content, null).stream().map(Action::label).toList();
        assertEquals(List.of("Copy address"), labels);
    }

    @Test
    void wifiOffersNameAndPasswordWithCaution() {
        List<Action> actions = actions("WIFI:T:WPA;S:HomeNet;P:secret123;;");
        assertEquals(List.of("Copy network name", "Copy password"), actions.stream().map(Action::label).toList());
        assertTrue(actions.get(0).caution().isEmpty());
        assertFalse(actions.get(1).caution().isEmpty());   // password: warn about the clipboard
    }

    @Test
    void openWifiHasNoPasswordButton() {
        assertEquals(List.of("Copy network name"), labels("WIFI:T:nopass;S:CafeGuest;;"));
    }

    @Test
    void emailCanBeWrittenOrCopied() {
        assertEquals(List.of("Write email", "Copy address"), labels("mailto:someone@example.com?subject=Hi"));
    }

    @Test
    void phoneCanBeCopied() {
        assertEquals(List.of("Copy number"), labels("tel:+91 98765 43210"));
    }

    @Test
    void twoFactorSecretIsCopyOnlyWithCaution() {
        List<Action> actions = actions("otpauth://totp/Example:me?secret=JBSWY3DPEHPK3PXP");
        assertEquals(List.of("Copy secret"), actions.stream().map(Action::label).toList());
        assertFalse(actions.get(0).caution().isEmpty());
    }

    @Test
    void plainTextCanBeCopied() {
        assertEquals(List.of("Copy text"), labels("Hello there"));
    }

    @Test
    void otherSchemesAreNeverOpenable() {
        // These aren't URLs to the app at all, so no "Open" can ever appear.
        assertEquals(List.of("Copy text"), labels("javascript:alert(1)"));
        assertEquals(List.of("Copy text"), labels("file:///C:/Windows/System32/calc.exe"));
    }
}
