package com.qrassistant.ui;

import com.qrassistant.action.Action;
import com.qrassistant.content.QrContent;
import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.UrlReport;

import java.time.ZoneId;
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
 */
public record PreviewModel(String kind, String headlineLabel, String headline,
                           List<PreviewDetails.Row> rows, List<Indicator> indicators, String checksSummary,
                           boolean hadHiddenCharacters, List<Action> actions, boolean canSelectArea) {

    public PreviewModel {
        rows = List.copyOf(rows);
        indicators = List.copyOf(indicators);
        actions = List.copyOf(actions);
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

        return new PreviewModel(ContentLabels.kindOf(content), details.headlineLabel(), details.headline(),
                details.rows(), indicators, checksSummary(report), details.hadHiddenCharacters(),
                actions, canSelectArea);
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
