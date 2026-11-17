package com.qrassistant.app;
import com.qrassistant.action.Action;
import com.qrassistant.action.ActionManager;
import com.qrassistant.content.ContentRecognizer;
import com.qrassistant.content.QrContent;
import com.qrassistant.capture.CapturedScreen;
import com.qrassistant.capture.ScreenCaptureException;
import com.qrassistant.capture.ScreenCapturer;
import com.qrassistant.scan.DecodedQr;
import com.qrassistant.scan.QrScanner;
import com.qrassistant.ui.OverlayMode;
import com.qrassistant.ui.OverlayResult;
import com.qrassistant.ui.PreviewChoice;
import com.qrassistant.ui.PreviewModel;
import com.qrassistant.ui.UiGateway;
import com.qrassistant.content.UrlContent;
import com.qrassistant.urlcheck.UrlReport;
import com.qrassistant.urlcheck.UrlSafetyChecker;
import java.awt.Rectangle;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * The controller: runs a scan from start to finish.
 *
 * All scan work happens on ONE background "worker" thread, so the UI never freezes.
 * The workflow talks to the UI only through UiGateway.
 *
 * Rule: this class CALLS the modules in the right order. It never does their work itself.
 */
public class ScanWorkflow {

    private static final Logger LOG = Logger.getLogger(ScanWorkflow.class.getName());

    /** How long to wait after hiding our windows before capturing, so Windows has repainted. (S4) */
    private static final long HIDE_BEFORE_CAPTURE_MS = 200;

    private final UiGateway ui;
    private final ScreenCapturer screenCapturer;
    private final QrScanner qrScanner;
    private final ContentRecognizer contentRecognizer;
    private final UrlSafetyChecker urlSafetyChecker;
    private final ActionManager actionManager;
    /** One worker thread. "Daemon" = it never keeps the program alive on its own. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "scan-worker");
        thread.setDaemon(true);
        return thread;
    });

    /** true while a scan is running; extra shortcut presses/clicks are ignored meanwhile. (I3) */
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);

    public ScanWorkflow(UiGateway ui, ScreenCapturer screenCapturer, QrScanner qrScanner,
                        ContentRecognizer contentRecognizer, UrlSafetyChecker urlSafetyChecker,
                        ActionManager actionManager) {
        this.ui = ui;
        this.screenCapturer = screenCapturer;
        this.qrScanner = qrScanner;
        this.contentRecognizer = contentRecognizer;
        this.urlSafetyChecker = urlSafetyChecker;
        this.actionManager = actionManager;
    }
    // --- Entry points: may be called from ANY thread; they only hand the job to the worker ---

    public void requestScreenScan() {
        // compareAndSet(false, true): "if no scan is running, mark one as running" - in one step,
        // so two quick clicks can never both get through.
        if (!scanInProgress.compareAndSet(false, true)) {
            return;
        }
        worker.execute(() -> runSafely(this::scanScreen));
    }

    /** Stops the worker. Called once when the app quits. */
    public void shutdown() {
        worker.shutdownNow();
    }

    // --- Smart scan (runs on the worker thread) ---

    private void scanScreen() throws ScreenCaptureException, InterruptedException {
        ui.clearProblem();
        ui.hideAppWindows();

        // On the worker thread, sleeping is fine: the UI thread stays free to repaint.
        Thread.sleep(HIDE_BEFORE_CAPTURE_MS);

        CapturedScreen captured = screenCapturer.captureMonitorUnderMouse();
        List<DecodedQr> codes = qrScanner.scan(captured.image());

        // Exactly one code: straight to the preview. Unless the user says "Not this one", we're done.
        if (codes.size() == 1 && !previewWantsSelectArea(codes.get(0))) {
            return;
        }
        chooseOnScreen(captured, codes);   // none, several, or "Not this one? Select area"
    }

    /**
     * Shows the overlay until the user has seen a preview and is done with it, or cancels.
     * Drawing a box re-scans just that area; depending on what's in it we preview it,
     * show the codes found inside it, or ask for a new box.
     *
     * @param allCodes every code found on the whole screen; outlined again after "Select area"
     */
    private void chooseOnScreen(CapturedScreen captured, List<DecodedQr> allCodes) {
        List<DecodedQr> shown = allCodes;
        OverlayMode currentMode = allCodes.isEmpty() ? OverlayMode.DRAW : OverlayMode.PICK;

        while (true) {
            OverlayResult outcome = ui.showOverlay(captured, shown, currentMode);
            DecodedQr chosen = null;

            // A "switch" over a sealed interface: the compiler checks that all three cases are handled.
            switch (outcome) {
                case OverlayResult.Picked picked -> chosen = picked.qr();
                case OverlayResult.Cancelled cancelled -> {
                    ui.showStatus("Scan cancelled.");
                    return;
                }
                case OverlayResult.Region region -> {
                    List<DecodedQr> inBox = scanArea(captured, region.area());
                    if (inBox.size() == 1) {
                        chosen = inBox.get(0);
                    } else {
                        // None in the box: ask again. Several: outline just those and let the user click.
                        shown = inBox;
                        currentMode = inBox.isEmpty() ? OverlayMode.DRAW_AGAIN : OverlayMode.PICK;
                    }
                }
            }

            if (chosen != null) {
                if (!previewWantsSelectArea(chosen)) {
                    return;
                }
                // "Not this one": back to the overlay, with everything found on the screen outlined.
                shown = allCodes;
                currentMode = allCodes.isEmpty() ? OverlayMode.DRAW : OverlayMode.PICK;
            }
        }
    }

    /** Scans only part of the screenshot; positions come back in FULL-screenshot pixels. */
    private List<DecodedQr> scanArea(CapturedScreen captured, Rectangle area) {
        List<DecodedQr> found = qrScanner.scan(captured.crop(area));
        return found.stream()
                .map(code -> code.movedBy(area.x, area.y))
                .toList();
    }

    /**
     * One code has been chosen: work out what it is, check it if it's a URL, show the preview,
     * and do whatever the user picks there.
     *
     * @return true if the user clicked "Not this one? Select area"
     */
    private boolean previewWantsSelectArea(DecodedQr code) {
        QrContent content = contentRecognizer.recognize(code.text());

        // URL checks only apply to URLs. They're offline and fast, so they run right here.
        UrlReport report = null;
        if (content instanceof UrlContent url) {
            report = urlSafetyChecker.check(url.url());
        }

        List<Action> actions = actionManager.actionsFor(content, report);
        PreviewModel model = PreviewModel.build(content, report, actions, true, ZoneId.systemDefault());

        return switch (ui.showPreview(model)) {
            case PreviewChoice.Cancel cancel -> {
                ui.showStatus("Closed without doing anything.");
                yield false;
            }
            case PreviewChoice.SelectArea selectArea -> true;
            case PreviewChoice.RunAction run -> {
                // Runs here on the worker, and only because the user clicked it. (Security rule 3)
                ui.showStatus(run.action().run());
                yield false;
            }
        };
    }

    // --- The single safety net (A6) ---

    /** A piece of scan work that may fail in the ways we expect. */
    @FunctionalInterface
    private interface ScanTask {
        void run() throws ScreenCaptureException, InterruptedException;
    }

    /**
     * Runs a scan so that, whatever happens, the user is told and the app is left clean.
     * Errors are logged by TYPE only - never QR text or screen content. (X8)
     */
    private void runSafely(ScanTask task) {
        try {
            task.run();
        } catch (ScreenCaptureException e) {
            showProblemSafely("Screen capture isn't available: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // the app is shutting down; just stop quietly
        } catch (RuntimeException e) {
            LOG.warning("Scan failed: " + e.getClass().getName());
            showProblemSafely("Something went wrong (" + e.getClass().getSimpleName() + ").");
        } finally {
            closeScanWindowsSafely();    // the overlay must never stay stuck on screen (I1)
            restoreWindowsSafely();
            scanInProgress.set(false);   // always, so the next scan can start
        }
    }

    // The UI itself might be what failed, so these must never throw.

    private void showProblemSafely(String message) {
        try {
            ui.showProblem(message);
        } catch (RuntimeException e) {
            LOG.warning("Could not show problem message: " + e.getClass().getName());
        }
    }

    private void closeScanWindowsSafely() {
        try {
            ui.closeScanWindows();
        } catch (RuntimeException e) {
            LOG.warning("Could not close scan windows: " + e.getClass().getName());
        }
    }

    private void restoreWindowsSafely() {
        try {
            ui.restoreAppWindows();
        } catch (RuntimeException e) {
            LOG.warning("Could not restore windows: " + e.getClass().getName());
        }
    }
}