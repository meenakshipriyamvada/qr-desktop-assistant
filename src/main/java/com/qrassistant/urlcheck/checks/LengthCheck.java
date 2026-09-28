package com.qrassistant.urlcheck.checks;

import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.Indicator.Level;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.UrlCheck;

import java.util.List;

/** Very long addresses can push the real site name out of view. (U9) */
public class LengthCheck implements UrlCheck {

    private static final int LONG = 200;
    private static final int VERY_LONG = 2000;

    @Override
    public List<Indicator> check(ParsedUrl url) {
        int length = url.original().length();
        if (length > VERY_LONG) {
            return List.of(new Indicator(Level.WARN, "Extremely long address (" + length
                    + " characters). Check the site name carefully - don't trust the start of the address."));
        }
        if (length > LONG) {
            return List.of(new Indicator(Level.INFO, "Long address (" + length
                    + " characters). Check the site name, not just the start of the address."));
        }
        return List.of();
    }
}