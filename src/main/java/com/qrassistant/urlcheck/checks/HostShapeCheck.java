package com.qrassistant.urlcheck.checks;

import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.Indicator.Level;
import com.qrassistant.urlcheck.Ipv4Parser;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.UrlCheck;

import java.util.ArrayList;
import java.util.List;

/**
 * Does the site name have a normal shape? (U9)
 *
 *  - no dot at all ("http://intranet/")     -> WARN: not a public website name
 *  - characters like _ % ! in the name       -> WARN: normal website names don't use them
 *  - many parts ("paypal.com.login.x.evil.ru") -> INFO: the part at the END decides who owns the
 *    site; the start is just decoration that anyone can choose
 */
public class HostShapeCheck implements UrlCheck {

    /** From this many dot-separated parts on, remind the user to read the name from the right. */
    private static final int MANY_PARTS = 5;

    @Override
    public List<Indicator> check(ParsedUrl url) {
        String host = url.host();
        if (url.ipv6Literal() || Ipv4Parser.parse(host).isPresent()) {
            return List.of();   // IP addresses are handled by IpAddressCheck
        }
        String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (name.equals("localhost") || name.endsWith(".localhost")) {
            return List.of();   // handled by PrivateNetworkCheck
        }

        List<Indicator> found = new ArrayList<>();
        if (!name.contains(".")) {
            found.add(new Indicator(Level.WARN, "\"" + name + "\" is not a normal public website name."));
        }
        if (!name.chars().allMatch(HostShapeCheck::isNormalHostCharacter)) {
            found.add(new Indicator(Level.WARN,
                    "The site name contains unusual characters that normal website names don't use."));
        }
        if (name.split("\\.").length >= MANY_PARTS) {
            found.add(new Indicator(Level.INFO, "The site name has many parts: \"" + url.hostUnicode()
                    + "\". Read it from the RIGHT: the ending decides who really owns the site."));
        }
        return found;
    }

    /** Letters a-z, digits, '-' and '.' (the host is already in lower-case ASCII form here). */
    private static boolean isNormalHostCharacter(int c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.';
    }
}