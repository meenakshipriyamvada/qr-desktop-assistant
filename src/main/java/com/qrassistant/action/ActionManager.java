package com.qrassistant.action;

import com.qrassistant.action.actions.CopyAction;
import com.qrassistant.content.QrContent;
import com.qrassistant.content.TextContent;
import com.qrassistant.content.WifiContent;
import com.qrassistant.system.ClipboardService;

import java.util.List;

/**
 * Decides which actions to offer for a QR code.
 *
 * Step 10: only "Copy text". Step 11 adds one provider per content type
 * (Open website, Send email, Copy password, ...).
 */
public class ActionManager {

    private final ClipboardService clipboard;

    public ActionManager(ClipboardService clipboard) {
        this.clipboard = clipboard;
    }

    public List<Action> actionsFor(QrContent content) {
        return List.of(new CopyAction(clipboard, "Copy text", content.rawText(), copyCaution(content)));
    }

    /** The clipboard isn't private: other apps and Windows' clipboard history can read it. (X5) */
    private static String copyCaution(QrContent content) {
        return switch (content) {
            case WifiContent wifi when !wifi.password().isEmpty() ->
                    "Copying includes the Wi-Fi password. Other apps can read the clipboard.";
            case TextContent text when text.sensitive() ->
                    "Copying includes the secret. Other apps can read the clipboard.";
            default -> "";
        };
    }
}
