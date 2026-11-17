package com.qrassistant.system;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Hands addresses to Windows: web pages to the default browser, emails to the default mail app.
 * The ONLY place the app opens anything outside itself.
 *
 * LOCK #2 (U1): this class checks the address AGAIN, on its own, right before opening it.
 * Lock #1 (UrlReport.canOpen) decides whether the button is offered at all. If a bug ever
 * let a bad address past lock #1, this still refuses it. Why it matters: on Windows,
 * "opening" a file: or other address can start a program, not just show a page.
 *
 * Errors are logged by TYPE only - never the address. (X8)
 */
public class DesktopService {

    private static final Logger LOG = Logger.getLogger(DesktopService.class.getName());

    /** What happened when we tried to open something. */
    public enum Result {
        OPENED,    // handed to Windows
        REFUSED,   // lock #2 said no
        FAILED     // allowed, but Windows couldn't do it (no browser / mail app, ...)
    }

    /** Opens a web page in the default browser. Only http and https are ever opened. */
    public Result openWebPage(String url) {
        Optional<URI> uri = webAddress(url);
        if (uri.isEmpty()) {
            return Result.REFUSED;
        }
        return launch(Desktop.Action.BROWSE, desktop -> desktop.browse(uri.get()));
    }

    /** Opens a new, unsent email in the default mail app. Only mailto: is ever opened. */
    public Result composeEmail(URI mailto) {
        if (!"mailto".equalsIgnoreCase(mailto.getScheme())) {
            return Result.REFUSED;
        }
        return launch(Desktop.Action.MAIL, desktop -> desktop.mail(mailto));
    }

    /**
     * Lock #2 for web pages: an http/https address with a host part, or nothing.
     * Deliberately does NOT reuse the URL checks' code, so one bug can't open both locks.
     */
    static Optional<URI> webAddress(String url) {
        try {
            URI uri = new URI(url);   // rejects spaces, line breaks and control characters
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean web = scheme.equals("http") || scheme.equals("https");
            boolean hasHost = uri.getRawAuthority() != null && !uri.getRawAuthority().isEmpty();
            return web && hasHost ? Optional.of(uri) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();   // never log e.getMessage(): it contains the address (X8)
        }
    }

    // --- Talking to Windows ---

    @FunctionalInterface
    private interface DesktopCall {
        void run(Desktop desktop) throws IOException;
    }

    private static Result launch(Desktop.Action needed, DesktopCall call) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(needed)) {
                return Result.FAILED;
            }
            call.run(Desktop.getDesktop());
            return Result.OPENED;
        } catch (IOException | RuntimeException e) {
            LOG.warning("Desktop " + needed + " failed: " + e.getClass().getName());
            return Result.FAILED;
        }
    }
}
