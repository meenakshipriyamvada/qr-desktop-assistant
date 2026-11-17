package com.qrassistant.ui;

import com.qrassistant.capture.CapturedScreen;
import com.qrassistant.scan.DecodedQr;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
/**
 * Everything the scan workflow needs from the user interface.
 *
 * RULES
 *  - Call these methods from the scan WORKER thread, never from the JavaFX thread.
 *  - Each method waits until the UI has actually done the job before returning,
 *    so the workflow's code runs in a simple top-to-bottom order.
 *
 * Because this is an interface, tests can use a fake UI that needs no windows.
 * More methods are added in later steps (file chooser, redirect results).
 */
public interface UiGateway {

    /** Hides the app's own windows before a screen capture, remembering which were open. */
    void hideAppWindows();

    /** Shows again whichever app windows were open before hideAppWindows(). */
    void restoreAppWindows();

    /** Shows a normal status message. */
    void showStatus(String message);

    /** Shows a problem message together with the "Try again" button. */
    void showProblem(String message);

    /** Hides the "Try again" button (a new attempt has started). */
    void clearProblem();

    /**
     * Shows the frozen-screen overlay and WAITS until the user picks a code, drags a box or cancels.
     *
     * @param codes codes to outline (may be empty)
     */
    OverlayResult showOverlay(CapturedScreen captured, List<DecodedQr> codes, OverlayMode mode);

    /** Shows the "Open image" dialog and WAITS. Empty if the user cancelled. */
    Optional<Path> chooseImageFile();

    /**
     * Shows a list of codes found in an image file and WAITS for the user to pick one.
     *
     * @param labels one sanitized line per code
     * @return the chosen position in the list, or empty if the user cancelled
     */
    OptionalInt chooseCode(List<String> labels);

    /** Shows the preview window and WAITS until the user clicks a button or closes it. */
    PreviewChoice showPreview(PreviewModel model);

    /**
     * Shows a new model in the preview that is still open (after "Check where this goes")
     * and WAITS for the user's next choice. Returns Cancel if the user closed the preview meanwhile.
     */
    PreviewChoice updatePreview(PreviewModel model);

    /** Closes the overlay, the code list and the preview if open. Used by the workflow's safety net; never throws. */
    void closeScanWindows();
}
