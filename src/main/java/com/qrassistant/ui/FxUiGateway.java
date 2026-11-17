package com.qrassistant.ui;

import com.qrassistant.capture.CapturedScreen;
import com.qrassistant.scan.DecodedQr;
import javafx.application.Platform;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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

    /** Was the main window open when hideAppWindows() was called? Only touched on the FX thread. */
    private boolean mainWindowWasShowing;

    public FxUiGateway(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
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
            // A scan started from the shortcut or tray may have no window open: a problem
            // must still be seen, so bring the window up.
            mainWindow.show();
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
        return waitForUser(pending);
    }

    @Override
    public Optional<Path> chooseImageFile() {
        // FileChooser waits by itself (a "nested" event loop), so one call does it all.
        return callOnFx(() -> ImageFileChooser.choose(mainWindow.owner()));
    }

    @Override
    public OptionalInt chooseCode(List<String> labels) {
        CompletableFuture<Integer> pending = callOnFx(() -> QrChooserDialog.open(labels));
        int index = waitForUser(pending);
        return index == QrChooserDialog.CANCELLED ? OptionalInt.empty() : OptionalInt.of(index);
    }

    @Override
    public PreviewChoice showPreview(PreviewModel model) {
        // Same two steps as the overlay: open on the FX thread, then wait here for the click.
        CompletableFuture<PreviewChoice> pending = callOnFx(() -> PreviewWindow.open(model));
        return waitForUser(pending);
    }

    @Override
    public PreviewChoice updatePreview(PreviewModel model) {
        CompletableFuture<PreviewChoice> pending = callOnFx(() -> PreviewWindow.update(model));
        return waitForUser(pending);
    }

    @Override
    public void closeScanWindows() {
        // Fire-and-forget: we don't wait, so this can't get stuck even if the UI is in trouble.
        Platform.runLater(() -> {
            SelectionOverlay.closeAny();
            QrChooserDialog.closeAny();
            PreviewWindow.closeAny();
        });
    }

    /** Waits (on the worker) for a window to report what the user did. On failure, closes our windows. */
    private <T> T waitForUser(CompletableFuture<T> pending) {
        try {
            return pending.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeScanWindows();
            throw new IllegalStateException("Interrupted while waiting for the user", e);
        } catch (ExecutionException e) {
            closeScanWindows();
            throw new IllegalStateException("Window failed", e.getCause());
        }
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