package com.qrassistant.ui;

import com.qrassistant.capture.CapturedScreen;
import com.qrassistant.content.QrContent;
import com.qrassistant.scan.DecodedQr;
import com.qrassistant.system.ClipboardService;
import javafx.application.Platform;
import com.qrassistant.urlcheck.UrlReport;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * The real UiGateway: runs each request on the JavaFX thread and waits for it to finish.
 *
 * This is the ONE place where the app hops from the worker thread to the JavaFX thread.
 */
public class FxUiGateway implements UiGateway {

    private final MainWindow mainWindow;
    private final ClipboardService clipboard;   // TEMPORARY use: the test dialog's Copy button

    /** Was the main window open when hideAppWindows() was called? Only touched on the FX thread. */
    private boolean mainWindowWasShowing;

    public FxUiGateway(MainWindow mainWindow, ClipboardService clipboard) {
        this.mainWindow = mainWindow;
        this.clipboard = clipboard;
    }

    @Override
    public void hideAppWindows() {
        runOnFx(() -> {
            mainWindowWasShowing = mainWindow.isShowing();
            mainWindow.hide();
        });
    }

    @Override
    public void restoreAppWindows() {
        runOnFx(() -> {
            if (mainWindowWasShowing) {
                mainWindow.show();
                // Reset, so a second restore (e.g. from the workflow's safety net) does nothing
                // instead of pulling the main window in front of other windows again.
                mainWindowWasShowing = false;
            }
        });
    }

    @Override
    public void showStatus(String message) {
        runOnFx(() -> mainWindow.setStatus(message));
    }

    @Override
    public void showProblem(String message) {
        runOnFx(() -> {
            mainWindow.setStatus(message + " Click \"Try again\" to retry.");
            mainWindow.showTryAgainButton(true);
        });
    }

    @Override
    public void clearProblem() {
        runOnFx(() -> mainWindow.showTryAgainButton(false));
    }

    @Override
    public OverlayResult showOverlay(CapturedScreen captured, List<DecodedQr> codes, OverlayMode mode) {
        // Step 1 (on the FX thread): open the overlay. That returns at once with a "pending" result.
        CompletableFuture<OverlayResult> pending =
                callOnFx(() -> SelectionOverlay.open(captured, codes, mode));

        // Step 2 (here, on the worker): wait until the user has picked, dragged or cancelled.
        try {
            return pending.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeOverlay();
            throw new IllegalStateException("Interrupted while waiting for the overlay", e);
        } catch (ExecutionException e) {
            closeOverlay();
            throw new IllegalStateException("Overlay failed", e.getCause());
        }
    }

    @Override
    public void closeOverlay() {
        // Fire-and-forget: we don't wait, so this can't get stuck even if the UI is in trouble.
        Platform.runLater(SelectionOverlay::closeAny);
    }

    @Override
    public void showResultDebug(QrContent content, UrlReport report) {
        runOnFx(() -> ResultDebugDialog.show(content, report, clipboard));
    }

    // --- Thread hopping ---

    private void runOnFx(Runnable action) {
        callOnFx(() -> {
            action.run();
            return null;
        });
    }

    /** Runs the task on the JavaFX thread and waits for its result (see FxThread). */
    private static <T> T callOnFx(Callable<T> task) {
        if (Platform.isFxApplicationThread()) {
            // The gateway is for the WORKER thread. Called on the FX thread, the workflow would end up
            // waiting for the user while blocking the very thread that handles the user's clicks.
            throw new IllegalStateException("UiGateway must not be called on the JavaFX thread");
        }
        return FxThread.call(task);
    }
}