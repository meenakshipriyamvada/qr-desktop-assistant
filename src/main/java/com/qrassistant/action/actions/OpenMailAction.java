package com.qrassistant.action.actions;

import com.qrassistant.action.Action;
import com.qrassistant.content.EmailContent;
import com.qrassistant.system.DesktopService;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Opens a new email in the default mail app. It is NOT sent: the user still has to press Send there.
 *
 * We build our OWN mailto: link from the address, subject and body the preview showed.
 * We never pass the QR's mailto: on as it is, because it could carry extra fields
 * the user never saw: cc, bcc, or "attach=", which some mail apps use to attach a local file.
 */
public final class OpenMailAction implements Action {

    /** One plain address. No characters that could start a new mailto field (? & ,) or hide one (% #). */
    private static final Pattern ADDRESS =
            Pattern.compile("[^\\s@:;,?&%#/<>\"]+@[^\\s@:;,?&%#/<>\"]+\\.[^\\s@:;,?&%#/<>\"]+");

    private final DesktopService desktop;
    private final URI mailto;

    private OpenMailAction(DesktopService desktop, URI mailto) {
        this.desktop = desktop;
        this.mailto = mailto;
    }

    /** @return the action, or empty if the address isn't one plain email address */
    public static Optional<Action> forEmail(DesktopService desktop, EmailContent email) {
        return mailtoFor(email).map(uri -> new OpenMailAction(desktop, uri));
    }

    /** "mailto:a@b.com?subject=Hi%20there&body=..." with ONLY the three fields the preview showed. */
    static Optional<URI> mailtoFor(EmailContent email) {
        String address = email.address().strip();
        if (!ADDRESS.matcher(address).matches()) {
            return Optional.empty();
        }
        List<String> fields = new ArrayList<>();
        if (!email.subject().isEmpty()) {
            fields.add("subject=" + encode(email.subject()));
        }
        if (!email.body().isEmpty()) {
            fields.add("body=" + encode(email.body()));
        }
        String link = "mailto:" + address + (fields.isEmpty() ? "" : "?" + String.join("&", fields));
        try {
            return Optional.of(new URI(link));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /** URLEncoder writes a space as "+", which mail apps show as a real "+". Mail links need %20. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public String label() {
        return "Write email";
    }

    @Override
    public String caution() {
        return "";
    }

    @Override
    public String run() {
        return switch (desktop.composeEmail(mailto)) {
            case OPENED -> "Opened a new email. It hasn't been sent.";
            case REFUSED -> "Not opened: this isn't an email link.";
            case FAILED -> "Couldn't open your mail app.";
        };
    }
}
