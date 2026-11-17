package com.qrassistant.ui;

import com.qrassistant.content.ContactContent;
import com.qrassistant.content.EmailContent;
import com.qrassistant.content.EventContent;
import com.qrassistant.content.PhoneContent;
import com.qrassistant.content.QrContent;
import com.qrassistant.content.TextContent;
import com.qrassistant.content.UnknownContent;
import com.qrassistant.content.UrlContent;
import com.qrassistant.content.WifiContent;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.UrlReport;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns one QR content into the label/value rows the preview shows.
 *
 * This is the place where QR text goes through DisplaySanitizer (X1): every value in a Row
 * is already safe to look at. Pure logic (no JavaFX).
 */
public final class PreviewDetails {

    /**
     * One line of the preview, e.g. "Network name: HomeWiFi".
     *
     * @param value       sanitized text, safe to display
     * @param maskedValue what to show while the value is hidden, or null if it isn't secret
     */
    public record Row(String label, String value, String maskedValue) {

        public boolean secret() {
            return maskedValue != null;
        }

        /** Records print all their fields by default; this one may hold a password, so it doesn't. (X8) */
        @Override
        public String toString() {
            return "Row[" + label + "]";
        }
    }

    /** Always the same length, so the mask doesn't reveal how long the secret is. */
    static final String MASK = "•".repeat(8);

    /** The secret part of an otpauth:// link ("secret=...") or of an export ("data=..."). */
    private static final Pattern OTP_SECRET = Pattern.compile("(?i)([?&](?:secret|data)=)[^&]*");

    private String headlineLabel = "";
    private String headline = "";
    private final List<Row> rows = new ArrayList<>();
    private boolean hadHiddenCharacters;

    private PreviewDetails() {
    }

    /**
     * @param report the URL checks, or null if the content isn't a URL
     * @param zone   the user's time zone, for event times
     */
    public static PreviewDetails of(QrContent content, UrlReport report, ZoneId zone) {
        PreviewDetails details = new PreviewDetails();
        switch (content) {
            case UrlContent url -> details.url(url, report);
            case WifiContent wifi -> details.wifi(wifi);
            case EmailContent email -> details.email(email);
            case PhoneContent phone -> details.add("Number", phone.number());
            case ContactContent contact -> details.contact(contact);
            case EventContent event -> details.event(event, zone);
            case TextContent text when text.sensitive() -> details.secretText(text);
            case TextContent text -> details.addMulti("Text", text.rawText());
            case UnknownContent unknown -> details.addMulti("Data", unknown.rawText());
        }
        return details;
    }

    /** Label for the big line at the top, e.g. "Real site". Empty if there is no big line. */
    public String headlineLabel() {
        return headlineLabel;
    }

    /** The big line itself (sanitized). Empty if there is none. */
    public String headline() {
        return headline;
    }

    public List<Row> rows() {
        return List.copyOf(rows);
    }

    /** Did any value contain hidden characters that had to be revealed? */
    public boolean hadHiddenCharacters() {
        return hadHiddenCharacters;
    }

    // --- One method per content type ---

    private void url(UrlContent url, UrlReport report) {
        Optional<ParsedUrl> parsed = report == null ? Optional.empty() : report.parsed();
        if (parsed.isPresent()) {
            ParsedUrl p = parsed.get();
            // The ASCII ("xn--") form is what the browser really connects to, so THAT is the big line.
            headlineLabel = "Real site";
            headline = single(p.host());
            if (!p.hostUnicode().equals(p.host())) {
                add("Looks like", p.hostUnicode());
            }
        } else {
            rows.add(new Row("Note", "The app couldn't read this address.", null));
        }
        add("Full address", url.url());
    }

    private void wifi(WifiContent wifi) {
        add("Network name", wifi.networkName());
        rows.add(new Row("Security", ContentLabels.wifiSecurity(wifi.security()), null));
        if (!wifi.password().isEmpty()) {
            rows.add(new Row("Password", single(wifi.password()), MASK));
        }
        if (wifi.hidden()) {
            rows.add(new Row("Hidden network", "Yes", null));
        }
    }

    private void email(EmailContent email) {
        add("To", email.address());
        add("Subject", email.subject());
        addMulti("Message", email.body());
    }

    private void contact(ContactContent contact) {
        add("Name", contact.name());
        add("Organization", contact.organization());
        addList("Phone", contact.phones());
        addList("Email", contact.emails());
        addList("Website", contact.websites());
    }

    private void event(EventContent event, ZoneId zone) {
        add("Title", event.title());
        if (!event.start().isBlank()) {
            add("Starts", EventDates.describe(event.start(), zone));
        }
        if (!event.end().isBlank()) {
            add("Ends", EventDates.describe(event.end(), zone));
        }
        add("Location", event.location());
        addMulti("Description", event.description());
    }

    /** 2FA codes: show the issuer and account, hide only the secret. If we can't find it, hide everything. */
    private void secretText(TextContent text) {
        String shown = multi(text.rawText());
        String masked = OTP_SECRET.matcher(shown).find()
                ? OTP_SECRET.matcher(shown).replaceAll("$1" + MASK)
                : MASK;
        rows.add(new Row("Content", shown, masked));
    }

    // --- Helpers: every value passes through the sanitizer here ---

    private void add(String label, String rawValue) {
        if (!rawValue.isBlank()) {
            rows.add(new Row(label, single(rawValue), null));
        }
    }

    private void addMulti(String label, String rawValue) {
        if (!rawValue.isBlank()) {
            rows.add(new Row(label, multi(rawValue), null));
        }
    }

    /** Each item is sanitized as ONE line first, so a fake line break can't pose as a second item. */
    private void addList(String label, List<String> rawValues) {
        List<String> shown = rawValues.stream()
                .filter(v -> !v.isBlank())
                .map(this::single)
                .toList();
        if (!shown.isEmpty()) {
            rows.add(new Row(label, String.join("\n", shown), null));
        }
    }

    private String single(String raw) {
        DisplaySanitizer.SanitizedText s = DisplaySanitizer.forSingleLine(raw);
        hadHiddenCharacters |= s.hadHiddenCharacters();
        return s.text();
    }

    private String multi(String raw) {
        DisplaySanitizer.SanitizedText s = DisplaySanitizer.forMultiLine(raw);
        hadHiddenCharacters |= s.hadHiddenCharacters();
        return s.text();
    }
}
