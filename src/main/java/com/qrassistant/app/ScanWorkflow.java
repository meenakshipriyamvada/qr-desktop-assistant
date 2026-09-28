package com.qrassistant.app;
import com.qrassistant.content.ContentRecognizer;
import com.qrassistant.content.QrContent;
import com.qrassistant.capture.CapturedScreen;
import com.qrassistant.capture.ScreenCaptureException;
import com.qrassistant.capture.ScreenCapturer;
import com.qrassistant.scan.DecodedQr;
import com.qrassistant.scan.QrScanner;
import com.qrassistant.ui.OverlayMode;
import com.qrassistant.ui.OverlayResult;
import com.qrassistant.ui.UiGateway;
import com.qrassistant.content.UrlContent;
import com.qrassistant.urlcheck.UrlReport;
import com.qrassistant.urlcheck.UrlSafetyChecker;
import java.awt.Rectangle;
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
    /** One worker thread. "Daemon" = it never keeps the program alive on its own. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "scan-worker");
        thread.setDaemon(true);
        return thread;
    });

    /** true while a scan is running; extra shortcut presses/clicks are ignored meanwhile. (I3) */
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);

    public ScanWorkflow(UiGateway ui, ScreenCapturer screenCapturer, QrScanner qrScanner,
                        ContentRecognizer contentRecognizer, UrlSafetyChecker urlSafetyChecker) {
        this.ui = ui;
        this.screenCapturer = screenCapturer;
        this.qrScanner = qrScanner;
        this.contentRecognizer = contentRecognizer;
        this.urlSafetyChecker = urlSafetyChecker;
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

        if (codes.size() == 1) {
            useCode(codes.get(0));                            // exactly one: no questions asked
        } else if (codes.isEmpty()) {
            chooseOnScreen(captured, codes, OverlayMode.DRAW);  // none: ask for a box
        } else {
            chooseOnScreen(captured, codes, OverlayMode.PICK);  // several: let the user click one
        }
    }

    /**
     * Shows the overlay until the user ends up with exactly one code, or cancels.
     * Drawing a box re-scans just that area; depending on what's in it we finish,
     * show the codes found inside it, or ask for a new box.
     */
    private void chooseOnScreen(CapturedScreen captured, List<DecodedQr> codes, OverlayMode mode) {
        List<DecodedQr> shown = codes;
        OverlayMode currentMode = mode;

        while (true) {
            OverlayResult outcome = ui.showOverlay(captured, shown, currentMode);

            // A "switch" over a sealed interface: the compiler checks that all three cases are handled.
            switch (outcome) {
                case OverlayResult.Picked picked -> {
                    useCode(picked.qr());
                    return;
                }
                case OverlayResult.Cancelled cancelled -> {
                    ui.showStatus("Scan cancelled.");
                    return;
                }
                case OverlayResult.Region region -> {
                    List<DecodedQr> inBox = scanArea(captured, region.area());
                    if (inBox.size() == 1) {
                        useCode(inBox.get(0));
                        return;
                    }
                    // None in the box: ask again. Several: outline just those and let the user click.
                    shown = inBox;
                    currentMode = inBox.isEmpty() ? OverlayMode.DRAW_AGAIN : OverlayMode.PICK;
                }
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

    /** One code has been chosen. TEMPORARY: steps 7-11 replace this with recognition + preview. */
    /** One code has been chosen: work out what it is. (Steps 9-11 add URL checks + the real preview.) */
    /** One code has been chosen: work out what it is, and check it if it's a URL. */
    private void useCode(DecodedQr code) {
        QrContent content = contentRecognizer.recognize(code.text());

        // URL checks only apply to URLs. They're offline and fast, so they run right here.
        UrlReport report = null;
        if (content instanceof UrlContent url) {
            report = urlSafetyChecker.check(url.url());
        }

        ui.restoreAppWindows();
        ui.showStatus("QR code chosen.");
        ui.showResultDebug(content, report);   // TEMPORARY until step 10
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
            closeOverlaySafely();        // the overlay must never stay stuck on screen (I1)
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

    private void closeOverlaySafely() {
        try {
            ui.closeOverlay();
        } catch (RuntimeException e) {
            LOG.warning("Could not close overlay: " + e.getClass().getName());
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