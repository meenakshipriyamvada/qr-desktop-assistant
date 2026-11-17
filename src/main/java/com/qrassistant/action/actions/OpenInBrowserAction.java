package com.qrassistant.action.actions;

import com.qrassistant.action.Action;
import com.qrassistant.system.DesktopService;

/**
 * Opens a web address in the default browser.
 *
 * Always the ORIGINAL address from the QR code, never a redirect target (step 12).
 * DesktopService checks it again before opening (lock #2).
 */
public final class OpenInBrowserAction implements Action {

    private final DesktopService desktop;
    private final String url;

    public OpenInBrowserAction(DesktopService desktop, String url) {
        this.desktop = desktop;
        this.url = url;
    }

    @Override
    public String label() {
        return "Open in browser";
    }

    @Override
    public String caution() {
        return "";
    }

    @Override
    public String run() {
        return switch (desktop.openWebPage(url)) {
            case OPENED -> "Opened in your browser.";
            case REFUSED -> "Not opened: this isn't an http or https address.";
            case FAILED -> "Couldn't open your browser.";
        };
    }
}
