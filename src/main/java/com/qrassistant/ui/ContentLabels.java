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

    /** Longest summary shown in a list, in characters. */
    static final int SUMMARY_LENGTH = 60;

    /**
     * One short, SAFE line for a list of codes, e.g. "Wi-Fi network: HomeNet".
     * Never shows a secret: Wi-Fi shows only the network name, 2FA codes show nothing.
     * Sanitized and shortened.
     */
    public static String summary(QrContent content) {
        String detail = switch (content) {
            case UrlContent url -> url.url();
            case WifiContent wifi -> wifi.networkName();
            case EmailContent email -> email.address();
            case PhoneContent phone -> phone.number();
            case ContactContent contact -> contact.name();
            case EventContent event -> event.title();
            case TextContent text when text.sensitive() -> "(hidden)";
            case TextContent text -> text.rawText();
            case UnknownContent unknown -> "";
        };
        String shown = shorten(DisplaySanitizer.forSingleLine(detail.strip()).text());
        return shown.isEmpty() ? kindOf(content) : kindOf(content) + ": " + shown;
    }

    /** Cuts long text, counting real characters (an emoji is one, not two Java chars). */
    private static String shorten(String text) {
        if (text.codePointCount(0, text.length()) <= SUMMARY_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, SUMMARY_LENGTH)) + "…";
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
