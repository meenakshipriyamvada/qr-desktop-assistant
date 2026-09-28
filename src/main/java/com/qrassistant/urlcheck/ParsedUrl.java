package com.qrassistant.urlcheck;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * A URL split into the parts the safety checks need.
 *
 * Built WITHOUT any network access: java.net.URI just reads the text.
 * (Never use java.net.URL for this: URL.equals() and hashCode() secretly do a DNS lookup.) (X6)
 *
 * @param original    the URL exactly as given
 * @param scheme      "http" or "https" (lower case)
 * @param userInfo    anything before an '@' in the address part, e.g. "google.com" in
 *                    https://google.com@evil.com - usually empty
 * @param host        the REAL site, in ASCII form: international names become "xn--..." (punycode).
 *                    For IPv6 addresses: without the [ ] brackets.
 * @param hostUnicode the same host as people would read it, e.g. "bücher.de" for "xn--bcher-kva.de"
 * @param ipv6Literal true if the host was written as [ IPv6 address ]
 * @param port        the port written in the URL, or -1 if none was written
 * @param rawQuery    the part after '?', still %-encoded, or null if there is none
 */
public record ParsedUrl(String original, String scheme, String userInfo, String host, String hostUnicode,
                        boolean ipv6Literal, int port, String rawQuery) {

    private static final int MAX_PORT = 65535;

    /**
     * @return the parts, or empty if the text can't be read as a URL with a host.
     *         Deliberately STRICT: things browsers quietly "fix" (spaces, backslashes...) are rejected,
     *         because those are exactly the tricks attackers use.
     */
    public static Optional<ParsedUrl> parse(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return Optional.empty();   // note: never log e.getMessage() - it contains the URL (X8)
        }
        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();   // "user@host:port" - the part after "//"
        if (scheme == null || authority == null || authority.isEmpty()) {
            return Optional.empty();
        }

        // 1. Anything before the LAST '@' is a username (browsers use the last '@' too).
        String userInfo = "";
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            userInfo = authority.substring(0, at);
            authority = authority.substring(at + 1);
        }

        // 2. Split host and port.
        String host;
        int port = -1;
        boolean ipv6 = authority.startsWith("[");
        String portText = "";
        if (ipv6) {
            int close = authority.indexOf(']');
            if (close < 0) {
                return Optional.empty();
            }
            host = authority.substring(1, close);
            String rest = authority.substring(close + 1);
            if (!rest.isEmpty()) {
                if (!rest.startsWith(":")) {
                    return Optional.empty();
                }
                portText = rest.substring(1);
            }
        } else {
            int colon = authority.lastIndexOf(':');
            host = colon >= 0 ? authority.substring(0, colon) : authority;
            portText = colon >= 0 ? authority.substring(colon + 1) : "";
        }
        if (!portText.isEmpty()) {
            port = parsePort(portText);
            if (port < 0) {
                return Optional.empty();
            }
        }
        if (host.isEmpty()) {
            return Optional.empty();
        }

        // 3. International names: IDN turns "bücher.de" into "xn--bcher-kva.de" and back.
        String asciiHost;
        String unicodeHost;
        if (ipv6) {
            asciiHost = host.toLowerCase(Locale.ROOT);
            unicodeHost = asciiHost;
        } else {
            try {
                asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
                unicodeHost = IDN.toUnicode(asciiHost);
            } catch (IllegalArgumentException e) {
                return Optional.empty();   // e.g. an empty part like "a..com", or a part that is too long
            }
        }
        return Optional.of(new ParsedUrl(url, scheme.toLowerCase(Locale.ROOT), userInfo,
                asciiHost, unicodeHost, ipv6, port, uri.getRawQuery()));
    }

    /** "8080" -> 8080; anything that isn't a valid port number -> -1. */
    private static int parsePort(String text) {
        if (text.length() > 5 || !text.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        int port = Integer.parseInt(text);
        return port <= MAX_PORT ? port : -1;
    }
}