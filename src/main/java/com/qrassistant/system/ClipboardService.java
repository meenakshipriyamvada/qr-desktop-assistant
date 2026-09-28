package com.qrassistant.system;

import com.qrassistant.ui.FxThread;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/**
 * Puts text on the system clipboard. The ONLY place the app touches the clipboard.
 *
 * The JavaFX clipboard may only be used on the JavaFX thread, so this class hops there itself;
 * callers can use it from any thread.
 *
 * PRIVACY: any app on the computer can read the clipboard, and Windows' clipboard history
 * (Win+V) and cloud sync may keep copies. We can't prevent that; the preview warns before
 * copying sensitive things like passwords. (X5) The copied text is never logged.
 */
public class ClipboardService {

    /**
     * @param text exactly what should be pasted later (the ORIGINAL text, not the sanitized one)
     * @return true if the clipboard accepted it
     */
    public boolean copy(String text) {
        return FxThread.call(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            return Clipboard.getSystemClipboard().setContent(content);
        });
    }
}