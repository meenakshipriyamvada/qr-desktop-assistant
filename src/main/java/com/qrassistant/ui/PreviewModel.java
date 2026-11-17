package com.qrassistant.ui;

import com.qrassistant.action.Action;
import com.qrassistant.content.QrContent;
import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.RedirectResolver;
import com.qrassistant.urlcheck.RedirectResult;
import com.qrassistant.urlcheck.UrlReport;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * EVERYTHING the preview window shows, worked out in advance.
 *
 * The window only draws this; it makes no decisions and never touches raw QR text.
 * Every string in here is already sanitized. Pure data (no JavaFX).
 *
 * @param kind                e.g. "Wi-Fi network"
 * @param headlineLabel       e.g. "Real site"; empty if there's no big line
 * @param headline            the big line (for URLs: the ASCII / "xn--" host)
 * @param rows                label/value lines
 * @param indicators          URL findings, sanitized, most serious first; empty for non-URLs
 * @param checksSummary       one sentence about the URL checks; empty for non-URLs
 * @param hadHiddenCharacters true if hidden characters were revealed somewhere (show a warning)
 * @param actions             the buttons to offer (Cancel is NOT one of them: it's always there)
 * @param canSelectArea       show "Not this one? Select area"? (false for image files, step 13)
 * @param canCheckRedirects   show "Check where this goes"? (openable URLs that haven't been checked yet)
 * @param redirectLines       what the redirect check found, sanitized; empty until it has run
 */
public record PreviewModel(String kind, String headlineLabel, String headline,
                           List<PreviewDetails.Row> rows, List<Indicator> indicators, String checksSummary,
                           boolean hadHiddenCharacters, List<Action> actions, boolean canSelectArea,
                           boolean canCheckRedirects, List<String> redirectLines) {

    public PreviewModel {
        rows = List.copyOf(rows);
        indicators = List.copyOf(indicators);
        actions = List.copyOf(actions);
        redirectLines = List.copyOf(redirectLines);
    }

    /**
     * @param report the URL checks, or null if the content isn't a URL
     */
    public static PreviewModel build(QrContent content, UrlReport report, List<Action> actions,
                                     boolean canSelectArea, ZoneId zone) {
        PreviewDetails details = PreviewDetails.of(content, report, zone);

        List<Indicator> indicators = report == null ? List.of() : report.indicators().stream()
                // Messages can quote parts of the URL, so they're sanitized too. (X1)
                .map(i -> new Indicator(i.level(), DisplaySanitizer.forSingleLine(i.message()).text()))
                .toList();

        boolean canCheckRedirects = report != null && report.canOpen();   // same rule as "Open" (lock #1)

        return new PreviewModel(ContentLabels.kindOf(content), details.headlineLabel(), details.headline(),
                details.rows(), indicators, checksSummary(report), details.hadHiddenCharacters(),
                actions, canSelectArea, canCheckRedirects, List.of());
    }

    /** The same preview, plus what the redirect check found. The check button goes away: it has run. */
    public PreviewModel withRedirect(RedirectResult result) {
        return new PreviewModel(kind, headlineLabel, headline, rows, indicators, checksSummary,
                hadHiddenCharacters, actions, canSelectArea, false, redirectLines(result));
    }

    /** Never says "safe" and always says "appeared to": a site can treat the app and a browser differently. */
    static List<String> redirectLines(RedirectResult result) {
        List<String> lines = new ArrayList<>();
        switch (result.ending()) {
            case NO_REDIRECT -> lines.add("It didn't redirect anywhere when checked just now.");
            case ARRIVED -> {
                lines.add("It appeared to redirect " + times(result.redirects()) + ", ending at:");
                addAddress(lines, result.last());
            }
            case TOO_MANY_REDIRECTS -> {
                lines.add("Stopped after " + RedirectResolver.MAX_REDIRECTS + " redirects. The next one pointed to:");
                addAddress(lines, result.last());
            }
            case STOPPED -> {
                if (result.redirects() == 0) {
                    lines.add("Not checked: " + single(result.reason()) + ".");
                } else {
                    lines.add("It appeared to redirect to:");
                    addAddress(lines, result.last());
                    lines.add("Not followed further: " + single(result.reason()) + ".");
                }
            }
            case NETWORK_ERROR -> {
                lines.add("Couldn't finish the check: " + single(result.reason()) + ".");
                if (result.redirects() > 0) {
                    lines.add("Before that, it appeared to redirect to:");
                    addAddress(lines, result.last());
                }
            }
        }
        lines.add("\"Open in browser\" still opens the original address from the QR code.");
        return lines;
    }

    /** The address on its own line, plus its real site if it differs from what it looks like. */
    private static void addAddress(List<String> lines, String url) {
        lines.add(single(url));
        ParsedUrl.parse(url).ifPresent(p -> lines.add("Real site there: " + single(p.host())));
    }

    private static String times(int redirects) {
        return redirects == 1 ? "once" : redirects + " times";
    }

    private static String single(String raw) {
        return DisplaySanitizer.forSingleLine(raw).text();
    }

    /** Never says "safe". The checks only look for known tricks. (X2) */
    private static String checksSummary(UrlReport report) {
        if (report == null) {
            return "";
        }
        boolean warned = report.indicators().stream()
                .anyMatch(i -> i.level() == Indicator.Level.DANGER || i.level() == Indicator.Level.WARN);
        return warned
                ? "Basic checks found warning signs. Read them before you continue."
                : "No problems found by basic checks. That does not prove the site is safe.";
    }
}
