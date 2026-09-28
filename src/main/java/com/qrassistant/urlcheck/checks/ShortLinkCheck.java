package com.qrassistant.urlcheck.checks;

import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.Indicator.Level;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.UrlCheck;

import java.util.List;
import java.util.Set;

/**
 * Link shorteners (bit.ly and friends) hide where you'll really end up. (U11)
 * The list covers common services; an unknown shortener just won't be flagged.
 */
public class ShortLinkCheck implements UrlCheck {

    private static final Set<String> SHORTENERS = Set.of(
            "bit.ly", "bitly.com", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly",
            "rebrand.ly", "cutt.ly", "shorturl.at", "rb.gy", "t.ly", "tiny.cc", "lnkd.in", "s.id",
            "qrco.de", "q-r.to", "short.io", "bl.ink", "v.gd", "shorte.st");

    @Override
    public List<Indicator> check(ParsedUrl url) {
        String host = url.host().startsWith("www.") ? url.host().substring(4) : url.host();
        if (!SHORTENERS.contains(host)) {
            return List.of();
        }
        return List.of(new Indicator(Level.INFO,
                "This is a short link: the address you'd really end up at is hidden."));
    }
}