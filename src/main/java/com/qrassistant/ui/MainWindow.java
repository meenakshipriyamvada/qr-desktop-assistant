package com.qrassistant.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The app's main window.
 *
 * It contains NO logic: it shows buttons and a status line,
 * and tells whoever created it when a button is clicked.
 */
public class MainWindow {

    private final Stage stage;
    private final Label statusLabel = new Label("Ready.");
    private final Button tryAgainButton;

    /**
     * @param stage        the window JavaFX gave us
     * @param onScanScreen called when "Scan screen" (or "Try again") is clicked
     * @param onScanFile   called when "Scan image file..." is clicked
     * @param onQuit       called when the user wants to quit
     */
    public MainWindow(Stage stage, Runnable onScanScreen, Runnable onScanFile, Runnable onQuit) {
        this.stage = stage;

        Label title = new Label("QR Desktop Assistant");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label shortcutHint = new Label("Shortcut: Ctrl + Shift + Q (coming in step 14)");
        shortcutHint.setStyle("-fx-text-fill: gray;");

        Button scanScreenButton = createWideButton("Scan screen", onScanScreen);
        Button scanFileButton = createWideButton("Scan image file\u2026", onScanFile);
        Button quitButton = createWideButton("Quit", onQuit);

        // "Try again" does the same as "Scan screen"; it is only shown after a capture problem.
        tryAgainButton = createWideButton("Try again", onScanScreen);
        showTryAgainButton(false);

        statusLabel.setWrapText(true);

        VBox root = new VBox(10,
                title,
                shortcutHint,
                scanScreenButton,
                scanFileButton,
                new Separator(),
                statusLabel,
                tryAgainButton,
                quitButton);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);

        stage.setTitle("QR Desktop Assistant");
        stage.setScene(new Scene(root, 360, 320));
        stage.setMinWidth(300);
        stage.setMinHeight(280);

        // Until the tray icon exists (step 14), the window's X button quits the app.
        // Otherwise, with implicit exit turned off, closing the window would leave
        // the app running invisibly with no way to get back to it.
        stage.setOnCloseRequest(event -> onQuit.run());
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    /** Hides the window without quitting (implicit exit is off). Used before screen capture. */
    public void hide() {
        stage.hide();
    }
    /** This window, for dialogs that should stay in front of it, or null while it's hidden. */
    public Window owner() {
        return stage.isShowing() ? stage : null;
    }

    /** Is the window currently open on screen? */
    public boolean isShowing() {
        return stage.isShowing();
    }

    /** Shows a message in the status line. Must be called on the JavaFX thread. */
    public void setStatus(String message) {
        requireFxThread("setStatus");
        statusLabel.setText(message);
    }

    /** Shows or hides the "Try again" button. Must be called on the JavaFX thread. */
    public void showTryAgainButton(boolean visible) {
        requireFxThread("showTryAgainButton");
        tryAgainButton.setVisible(visible);   // draw it or not
        tryAgainButton.setManaged(visible);   // reserve space for it or not (no empty gap when hidden)
    }

    private static void requireFxThread(String methodName) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("MainWindow." + methodName + " must be called on the JavaFX thread");
        }
    }

    private static Button createWideButton(String text, Runnable onClick) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);          // stretch to the window's width
        button.setOnAction(event -> onClick.run());
        return button;
    }
}