package com.qrassistant.action;

/**
 * Something the user can choose to do with a QR code, shown as a button in the preview.
 *
 * Actions run only after the user clicks them (security rule 3). Cancel is NOT an action:
 * closing the preview is always possible and never does anything.
 */
public interface Action {

    /** Button text, e.g. "Copy text". */
    String label();

    /** A warning shown under the buttons, e.g. "Copying includes the password". Empty if none. */
    String caution();

    /**
     * Does the job. Called on the scan WORKER thread, never the JavaFX thread.
     *
     * @return a short message for the status line, e.g. "Copied to the clipboard."
     *         Must not contain QR text.
     */
    String run();
}
