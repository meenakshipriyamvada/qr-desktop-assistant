package com.qrassistant.urlcheck;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Everything the URL checks found out.
 *
 * Note what is NOT here: a "safe" / "unsafe" verdict. Only facts. (X2)
 *
 * @param url        the URL as it appeared in the QR code
 * @param parsed     its parts - empty if it couldn't be read as a URL
 * @param indicators findings, most serious first
 */
public record UrlReport(String url, Optional<ParsedUrl> parsed, List<Indicator> indicators) {

    public UrlReport {
        indicators = List.copyOf(indicators);
    }

    /**
     * May the app offer "Open Website" at all? Only for URLs that could be read AND use http/https.
     * This is lock #1 of 2 (U1). Warnings don't block opening - the user decides, after seeing them.
     */
    public boolean canOpen() {
        return parsed.map(p -> {
            String scheme = p.scheme().toLowerCase(Locale.ROOT);
            return scheme.equals("http") || scheme.equals("https");
        }).orElse(false);
    }
}