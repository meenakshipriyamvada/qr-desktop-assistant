package com.qrassistant.ui;

import com.qrassistant.content.ContactContent;
import com.qrassistant.content.EmailContent;
import com.qrassistant.content.EventContent;
import com.qrassistant.content.PhoneContent;
import com.qrassistant.content.QrContent;
import com.qrassistant.content.TextContent;
import com.qrassistant.content.UnknownContent;
import com.qrassistant.content.UrlContent;
import com.qrassistant.content.WifiContent;

import java.util.Locale;

/**
 * The words the preview uses for each kind of content. All user-facing names live here,
 * so changing wording never means hunting through the window code.
 */
public final class ContentLabels {

    private ContentLabels() {
    }

    /** What kind of thing the QR code holds, e.g. "Wi-Fi network". */
    public static String kindOf(QrContent content) {
        // Switch over a SEALED type: if a new content type is added, this won't compile until it's handled.
        return switch (content) {
            case UrlContent url -> "Web address";
            case WifiContent wifi -> "Wi-Fi network";
            case EmailContent email -> "Email";
            case PhoneContent phone -> "Phone number";
            case ContactContent contact -> "Contact card";
            case EventContent event -> "Calendar event";
            case TextContent text when text.sensitive() -> "2FA setup code (secret)";
            case TextContent text -> "Text";
            case UnknownContent unknown -> "Unreadable data";
        };
    }

    /**
     * Wi-Fi security type in plain words. Never echoes the QR's own value back:
     * an unknown type just becomes "Other".
     */
    public static String wifiSecurity(String security) {
        return switch (security.strip().toUpperCase(Locale.ROOT)) {
            case "WPA", "WPA2" -> "WPA (password protected)";
            case "SAE", "WPA3" -> "WPA3 (password protected)";
            case "WEP" -> "WEP (old and easy to break)";
            case "NOPASS" -> "None (open network)";
            case "" -> "Not stated";
            default -> "Other";
        };
    }
}
