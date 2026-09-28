package com.qrassistant.urlcheck.checks;

import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.Indicator.Level;
import com.qrassistant.urlcheck.Ipv4Parser;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.UrlCheck;

import java.util.List;
import java.util.OptionalLong;

/**
 * Is the "site" really a raw IP address? Legitimate public websites almost always use a name. (U5)
 * Disguised forms (one big number, hex, octal...) are a strong sign of a trick.
 */
public class IpAddressCheck implements UrlCheck {

    @Override
    public List<Indicator> check(ParsedUrl url) {
        if (url.ipv6Literal()) {
            return List.of(new Indicator(Level.WARN, "Uses an IP address instead of a website name."));
        }
        OptionalLong ipv4 = Ipv4Parser.parse(url.host());
        if (ipv4.isEmpty()) {
            return List.of();   // a normal name
        }
        if (Ipv4Parser.isStandardForm(url.host())) {
            return List.of(new Indicator(Level.WARN, "Uses an IP address instead of a website name."));
        }
        return List.of(new Indicator(Level.DANGER,
                "Uses an IP address written in a disguised form. Browsers read \"" + url.host()
                        + "\" as " + Ipv4Parser.format(ipv4.getAsLong()) + "."));
    }
}