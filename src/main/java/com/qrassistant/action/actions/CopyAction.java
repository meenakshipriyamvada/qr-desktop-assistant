package com.qrassistant.action.actions;

import com.qrassistant.action.Action;
import com.qrassistant.system.ClipboardService;

/**
 * Copies a piece of text to the clipboard.
 *
 * A normal class, not a record, on purpose: a record's toString() would print the text,
 * which may be a password. (X8)
 */
public final class CopyAction implements Action {

    private final ClipboardService clipboard;
    private final String label;
    private final String text;
    private final String caution;

    /**
     * @param text    exactly what to copy: the ORIGINAL text, never the sanitized display text
     * @param caution warning to show next to the button, or ""
     */
    public CopyAction(ClipboardService clipboard, String label, String text, String caution) {
        this.clipboard = clipboard;
        this.label = label;
        this.text = text;
        this.caution = caution;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String caution() {
        return caution;
    }

    @Override
    public String run() {
        return clipboard.copy(text) ? "Copied to the clipboard." : "Couldn't copy to the clipboard.";
    }
}
