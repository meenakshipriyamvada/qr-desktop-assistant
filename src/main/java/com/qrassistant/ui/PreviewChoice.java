package com.qrassistant.ui;

import com.qrassistant.action.Action;

/**
 * How the preview window ended. "sealed", so the workflow's switch must handle every case.
 */
public sealed interface PreviewChoice {

    /** Cancel, Esc, Enter (Cancel is the default button), or the window's X. Nothing happens. */
    record Cancel() implements PreviewChoice {
    }

    /** "Not this one? Select area": go back to the overlay. */
    record SelectArea() implements PreviewChoice {
    }

    /** "Check where this goes": follow the URL's redirects, then show the preview again with the result. */
    record CheckRedirects() implements PreviewChoice {
    }

    /** The user clicked an action button. The WORKFLOW runs it, not the window. */
    record RunAction(Action action) implements PreviewChoice {
    }
}
