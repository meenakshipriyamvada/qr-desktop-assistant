package com.qrassistant.urlcheck.checks;

import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.Indicator.Level;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.UrlCheck;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Login tokens, passwords or keys inside the link, e.g. ?token=... (U10)
 *
 * Magic-login and password-reset links work like a key: opening one may sign you in, and anyone
 * you share it with could do the same. We only look at the parameter NAMES, never judge the values.
 */
public class SensitiveParamCheck implements UrlCheck {

    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "token", "access_token", "id_token", "refresh_token", "auth", "authorization",
            "session", "sessionid", "sid", "password", "passwd", "pwd", "pass", "secret",
            "key", "apikey", "api_key", "otp", "code", "sig", "signature", "magic", "reset");

    @Override
    public List<Indicator> check(ParsedUrl url) {
        if (url.rawQuery() == null || url.rawQuery().isEmpty()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        for (String pair : url.rawQuery().split("&")) {
            String name = decode(pair.split("=", 2)[0]).toLowerCase(Locale.ROOT);
            if (SENSITIVE_NAMES.contains(name)) {
                found.add(name);
            }
        }
        if (found.isEmpty()) {
            return List.of();
        }
        return List.of(new Indicator(Level.WARN, "The link contains what looks like a login code, key or password ("
                + String.join(", ", found) + "). Opening it may sign you in, and anyone you share it with could too."));
    }

    private static String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return text;
        }
    }
}