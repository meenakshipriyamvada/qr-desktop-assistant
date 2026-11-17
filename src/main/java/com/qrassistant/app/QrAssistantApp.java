package com.qrassistant.app;
import com.qrassistant.action.ActionManager;
import com.qrassistant.capture.ScreenCapturer;
import com.qrassistant.scan.ZxingQrScanner;
import com.qrassistant.ui.FxUiGateway;
import com.qrassistant.ui.MainWindow;
import com.qrassistant.ui.PreviewWindow;
import com.qrassistant.ui.SelectionOverlay;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import com.qrassistant.content.ContentRecognizer;
import java.util.logging.Logger;
import com.qrassistant.urlcheck.UrlSafetyChecker;
import com.qrassistant.system.ClipboardService;
import com.qrassistant.system.DesktopService;
/**
 * Entry point of QR Desktop Assistant.
 *
 * Responsibilities:
 *  - create the objects the app needs and connect them ("wiring")
 *  - own the one and only shutdown path
 *
 * It no longer does any scanning itself: that is ScanWorkflow's job.
 */
public class QrAssistantApp extends Application {

    private static final Logger LOG = Logger.getLogger(QrAssistantApp.class.getName());

    private MainWindow mainWindow;
    private ScanWorkflow workflow;

    @Override
    public void start(Stage primaryStage) {
        // By default JavaFX quits when the last window closes.
        // We turn that off: later the app must keep running in the tray
        // so Ctrl+Shift+Q still works with no window open. (Stage 4, I8)
        Platform.setImplicitExit(false);

        // Second safety net (I1): if any UI code crashes, make sure the full-screen overlay
        // can't stay stuck over the whole screen. We log the error TYPE and where it happened,
        // never its message (which could contain QR text). (X8)
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> {
            SelectionOverlay.closeAny();
            PreviewWindow.closeAny();
            StackTraceElement[] trace = error.getStackTrace();
            LOG.severe("Unexpected UI error: " + error.getClass().getName()
                    + (trace.length > 0 ? " at " + trace[0] : ""));
        });

        // The lambdas below are only CALLED when a button is clicked,
        // which is after 'workflow' has been created a few lines further down.
        mainWindow = new MainWindow(
                primaryStage,
                () -> workflow.requestScreenScan(),
                this::onScanFileRequested,
                this::quit);

        workflow = new ScanWorkflow(
                new FxUiGateway(mainWindow),
                new ScreenCapturer(),
                new ZxingQrScanner(),
                new ContentRecognizer(),
                new UrlSafetyChecker(),
                new ActionManager(new ClipboardService(), new DesktopService()));

        mainWindow.show();
    }

    private void onScanFileRequested() {
        mainWindow.setStatus("Image file scanning isn't built yet (step 13).");
    }

    // --- Shutdown ---

    /** The single way the app shuts down. Quit button, window close and (later) the tray all call this. */
    private void quit() {
        // Asks JavaFX to shut down. JavaFX then calls stop() below and ends the program.
        Platform.exit();
    }

    @Override
    public void stop() {
        // Called by JavaFX exactly once, after Platform.exit().
        if (workflow != null) {
            workflow.shutdown();
        }
        // Later steps add here:
        //  - unregister Ctrl+Shift+Q         (step 14)
        //  - remove the tray icon            (step 14)
    }

    public static void main(String[] args) {
        launch(args);
    }
}