package com.qrassistant.content.parsers;

import com.qrassistant.content.ContentParser;
import com.qrassistant.content.QrContent;
import com.qrassistant.content.WifiContent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recognizes Wi-Fi QR codes:
 *   WIFI:S:MyNetwork;T:WPA;P:mypassword;H:false;;
 *     S = network name, T = security (WPA / WEP / nopass), P = password, H = hidden network
 */
public class WifiParser implements ContentParser {

    private static final String PREFIX = "wifi:";

    @Override
    public Optional<QrContent> parse(String text) {
        String trimmed = text.strip();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            return Optional.empty();
        }
        Map<String, List<String>> fields = com.qrassistant.content.parsers.SemicolonFields.parse(trimmed.substring(PREFIX.length()));

        String networkName = com.qrassistant.content.parsers.SemicolonFields.first(fields, "S");
        if (networkName.isEmpty()) {
            return Optional.empty();   // a Wi-Fi code without a network name is useless -> plain text
        }
        String security = com.qrassistant.content.parsers.SemicolonFields.first(fields, "T").strip();
        if (security.equalsIgnoreCase("nopass")) {
            security = "nopass";
        } else {
            security = security.toUpperCase(Locale.ROOT);
        }
        String password = com.qrassistant.content.parsers.SemicolonFields.first(fields, "P");
        boolean hidden = com.qrassistant.content.parsers.SemicolonFields.first(fields, "H").strip().equalsIgnoreCase("true");

        return Optional.of(new WifiContent(text, networkName, security, password, hidden));
    }
}