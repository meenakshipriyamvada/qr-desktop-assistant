package com.qrassistant.urlcheck.checks;

import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.Indicator.Level;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.UrlCheck;

import java.util.List;

/** HTTPS (encrypted) or HTTP (not encrypted)? Anything else can't be opened at all. (U1, U4) */
public class SchemeCheck implements UrlCheck {

    @Override
    public List<Indicator> check(ParsedUrl url) {
        return switch (url.scheme()) {
            case "https" -> List.of(new Indicator(Level.OK, "Uses HTTPS: the connection is encrypted."));
            case "http" -> List.of(new Indicator(Level.WARN,
                    "Not encrypted (HTTP): others on the same network could see or change the page."));
            default -> List.of(new Indicator(Level.DANGER, "Not a web address (" + url.scheme() + "). It can't be opened."));
        };
    }
}