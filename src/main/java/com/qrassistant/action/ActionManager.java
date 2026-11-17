package com.qrassistant.action;

import com.qrassistant.action.actions.CopyAction;
import com.qrassistant.action.actions.OpenInBrowserAction;
import com.qrassistant.action.actions.OpenMailAction;
import com.qrassistant.content.ContactContent;
import com.qrassistant.content.EmailContent;
import com.qrassistant.content.EventContent;
import com.qrassistant.content.PhoneContent;
import com.qrassistant.content.QrContent;
import com.qrassistant.content.TextContent;
import com.qrassistant.content.UnknownContent;
import com.qrassistant.content.UrlContent;
import com.qrassistant.content.WifiContent;
import com.qrassistant.system.ClipboardService;
import com.qrassistant.system.DesktopService;
import com.qrassistant.urlcheck.UrlReport;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which actions to offer for a QR code: "what can the user do with THIS?"
 *
 * One provider method per content type, chosen by a switch over the sealed QrContent type,
 * so a new content type won't compile until someone decides its actions.
 *
 * The first action in the list is the main one. Cancel is never in the list: it's always there.
 */
public class ActionManager {

    private static final String CLIPBOARD_CAUTION = "Other apps can read the clipboard.";

    private final ClipboardService clipboard;
    private final DesktopService desktop;

    public ActionManager(ClipboardService clipboard, DesktopService desktop) {
        this.clipboard = clipboard;
        this.desktop = desktop;
    }

    /**
     * @param report the URL checks, or null if the content isn't a URL
     */
    public List<Action> actionsFor(QrContent content, UrlReport report) {
        return switch (content) {
            case UrlContent url -> forUrl(url, report);
            case WifiContent wifi -> forWifi(wifi);
            case EmailContent email -> forEmail(email);
            case PhoneContent phone -> List.of(copy("Copy number", phone.number(), ""));
            case TextContent text when text.sensitive() -> List.of(copy("Copy secret", text.rawText(),
                    "Copying puts the 2FA secret on the clipboard. " + CLIPBOARD_CAUTION));
            case ContactContent contact -> List.of(copyText(contact));
            case EventContent event -> List.of(copyText(event));
            case TextContent text -> List.of(copyText(text));
            case UnknownContent unknown -> List.of(copyText(unknown));
        };
    }

    // --- One provider per content type ---

    private List<Action> forUrl(UrlContent url, UrlReport report) {
        List<Action> actions = new ArrayList<>();
        // Lock #1: "Open" is only OFFERED for readable http/https addresses.
        if (report != null && report.canOpen()) {
            actions.add(new OpenInBrowserAction(desktop, url.url()));   // the ORIGINAL address
        }
        actions.add(copy("Copy address", url.url(), ""));
        return actions;
    }

    private List<Action> forWifi(WifiContent wifi) {
        List<Action> actions = new ArrayList<>();
        if (!wifi.networkName().isEmpty()) {
            actions.add(copy("Copy network name", wifi.networkName(), ""));
        }
        if (!wifi.password().isEmpty()) {
            actions.add(copy("Copy password", wifi.password(),
                    "Copying puts the Wi-Fi password on the clipboard. " + CLIPBOARD_CAUTION));
        }
        if (actions.isEmpty()) {
            actions.add(copyText(wifi));
        }
        return actions;
    }

    private List<Action> forEmail(EmailContent email) {
        List<Action> actions = new ArrayList<>();
        OpenMailAction.forEmail(desktop, email).ifPresent(actions::add);
        actions.add(email.address().isBlank()
                ? copyText(email)
                : copy("Copy address", email.address(), ""));
        return actions;
    }

    // --- Helpers ---

    private Action copyText(QrContent content) {
        return copy("Copy text", content.rawText(), "");
    }

    private Action copy(String label, String text, String caution) {
        return new CopyAction(clipboard, label, text, caution);
    }
}
