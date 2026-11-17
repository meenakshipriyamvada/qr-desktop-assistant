package com.qrassistant.app;
import com.qrassistant.action.ActionManager;
import com.qrassistant.capture.ImageFileLoader;
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
import com.qrassistant.urlcheck.RedirectResolver;
import com.qrassistant.urlcheck.UrlSafetyChecker;
import com.qrassistant.system.ClipboardService;
import com.qrassistant.system.DesktopService;
import com.qrassistant.system.GlobalShortcut;
import com.qrassistant.system.JIntellitypeShortcut;
import com.qrassistant.ui.AppTray;
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
    private final GlobalShortcut shortcut = new JIntellitypeShortcut();
    private AppTray tray;   // null if Windows offers no tray

    @Override
    public void start(Stage primaryStage) {
        // By default JavaFX quits when the last window closes.
        // We turn that off: the app keeps running in the tray
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
                () -> workflow.requestFileScan(),
                this::quit);

        workflow = new ScanWorkflow(
                new FxUiGateway(mainWindow),
                new ScreenCapturer(),
                new ZxingQrScanner(),
                new ContentRecognizer(),
                new UrlSafetyChecker(),
                new ActionManager(new ClipboardService(), new DesktopService()),
                new RedirectResolver(),
                new ImageFileLoader());

        // Ctrl+Shift+Q. requestScreenScan() only hands the job to the worker, so it's safe to
        // call from JIntellitype's thread and returns at once.
        boolean shortcutOk = shortcut.register(workflow::requestScreenScan);
        mainWindow.setShortcutHint(shortcutOk
                ? "Shortcut: Ctrl + Shift + Q (if nothing happens, another app may be using it)"
                : "The Ctrl + Shift + Q shortcut isn't available. Use the button or the tray icon.");

        // Tray menu callbacks arrive on AWT's thread: scans go to the worker (thread-safe),
        // window work goes to the JavaFX thread with Platform.runLater.
        tray = AppTray.install(
                workflow::requestScreenScan,
                workflow::requestFileScan,
                () -> Platform.runLater(mainWindow::show),
                () -> Platform.runLater(this::quit)).orElse(null);
        if (tray != null) {
            mainWindow.hideOnClose(tray::tellStillRunning);   // X now hides to the tray
        }

        mainWindow.show();
    }

    // --- Shutdown ---

    /** The single way the app shuts down. The Quit button and the tray's Quit call this. */
    private void quit() {
        // Asks JavaFX to shut down. JavaFX then calls stop() below and ends the program.
        Platform.exit();
    }

    @Override
    public void stop() {
        // Called by JavaFX exactly once, after Platform.exit().
        shortcut.unregister();   // Windows would otherwise keep Ctrl+Shift+Q reserved until we exit
        if (tray != null) {
            tray.remove();
        }
        if (workflow != null) {
            workflow.shutdown();
        }
        // AWT (the tray) can keep background threads alive after JavaFX has stopped,
        // which would leave an invisible process running. Everything is cleaned up above,
        // so end the program for certain.
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}