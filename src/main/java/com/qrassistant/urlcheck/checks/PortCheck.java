package com.qrassistant.urlcheck.checks;

import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.Indicator.Level;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.UrlCheck;

import java.util.List;

/**
 * An explicit, unusual port number, e.g. https://example.com:8443 (U14).
 * Sometimes legitimate, but normal public websites don't need one.
 */
public class PortCheck implements UrlCheck {

    @Override
    public List<Indicator> check(ParsedUrl url) {
        if (url.port() < 0) {
            return List.of();   // no port written
        }
        boolean defaultPort = (url.scheme().equals("https") && url.port() == 443)
                || (url.scheme().equals("http") && url.port() == 80);
        if (defaultPort) {
            return List.of();
        }
        return List.of(new Indicator(Level.INFO,
                "Uses an unusual port (" + url.port() + "). Normal websites don't need one."));
    }
}