package com.qrassistant.ui;

import com.qrassistant.action.Action;
import com.qrassistant.urlcheck.Indicator;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Shows what a QR code contains and asks what to do. Nothing happens until the user clicks.
 *
 * Safety features:
 *  - Cancel is the DEFAULT button: Enter and Esc both cancel.
 *  - Input guard: every other button is disabled for the first 400 ms, so a click or key press
 *    meant for the overlay can't land on an action by accident.
 *  - Secrets are masked until "Show" is clicked.
 *
 * "Check where this goes" doesn't close the window: it shows "Checking..." with every button
 * except Cancel disabled, and the workflow then calls update() with the result.
 *
 * The window makes no decisions. It draws a PreviewModel (already sanitized) and reports
 * which button was clicked. Runs on the JavaFX thread only.
 */
public final class PreviewWindow {

    private static final double WIDTH = 480;
    private static final Duration INPUT_GUARD = Duration.millis(400);
    /** The scrolling part never gets taller than this share of the screen. */
    private static final double MAX_SCREEN_SHARE = 0.6;

    /** The preview currently open, if any. Used to close it in emergencies. */
    private static PreviewWindow current;

    private final Stage stage = new Stage();
    /** The monitor the preview belongs on (the one under the mouse when it opened). */
    private final Rectangle2D screen = screenUnderMouse().getVisualBounds();
    /** Completed when the user makes a choice. A new one starts with each update(). */
    private CompletableFuture<PreviewChoice> round = new CompletableFuture<>();
    /** Buttons that stay disabled until the input guard ends. */
    private final List<ButtonBase> guarded = new ArrayList<>();
    private PauseTransition guard;
    private boolean finished;

    /**
     * Opens the preview and returns at once.
     * The future is completed (exactly once) when the user clicks something or closes the window.
     */
    public static CompletableFuture<PreviewChoice> open(PreviewModel model) {
        closeAny();   // never two previews at once
        PreviewWindow window = new PreviewWindow(model);
        current = window;
        window.show();
        return window.round;
    }

    /**
     * Shows a new model in the preview that's already open (after "Check where this goes"),
     * and returns a future for the user's NEXT choice. If the user closed the preview meanwhile,
     * the answer is simply Cancel.
     */
    public static CompletableFuture<PreviewChoice> update(PreviewModel model) {
        if (current == null) {
            return CompletableFuture.completedFuture(new PreviewChoice.Cancel());
        }
        current.round = new CompletableFuture<>();
        current.render(model);
        current.keepOnScreen();
        current.startGuard();
        return current.round;
    }

    /** Emergency exit: closes any open preview as "Cancel". Safe to call when none is open. */
    public static void closeAny() {
        if (current != null) {
            current.finish(new PreviewChoice.Cancel());
        }
    }

    private PreviewWindow(PreviewModel model) {
        stage.setAlwaysOnTop(true);   // may be opened by the shortcut while other apps are in front
        stage.setOnHidden(event -> finish(new PreviewChoice.Cancel()));   // the X button = Cancel
        // Centre on the monitor under the mouse: the one the user just scanned.
        stage.setOnShown(e -> {
            stage.setX(screen.getMinX() + (screen.getWidth() - stage.getWidth()) / 2);
            stage.setY(screen.getMinY() + (screen.getHeight() - stage.getHeight()) / 2);
        });
        render(model);
    }

    /** Builds the whole window content from the model. Called on open and on every update. */
    private void render(PreviewModel model) {
        guarded.clear();
        VBox body = new VBox(12);
        body.setPadding(new Insets(4, 12, 4, 4));

        if (model.hadHiddenCharacters()) {
            body.getChildren().add(banner("⚠ This code contains hidden characters that can disguise "
                    + "what it says. They are shown as ⟦U+…⟧."));
        }
        if (!model.headline().isEmpty()) {
            body.getChildren().add(headline(model.headlineLabel(), model.headline()));
        }
        body.getChildren().add(rowsGrid(model.rows()));
        if (!model.indicators().isEmpty() || !model.checksSummary().isEmpty()) {
            body.getChildren().add(checks(model.indicators(), model.checksSummary()));
        }
        if (!model.redirectLines().isEmpty()) {
            body.getChildren().add(redirectBox(model.redirectLines()));
        }

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Label kind = new Label(model.kind());
        kind.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        VBox root = new VBox(10, kind, scroll, cautions(model), buttons(model));
        root.setPadding(new Insets(14));
        root.setPrefWidth(WIDTH);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        stage.setTitle("QR code — " + model.kind());
        stage.setScene(new Scene(root));

        fitToContent(root, body, scroll);
    }

    // --- Building the parts ---

    private static Label banner(String text) {
        Label label = wrapping(text);
        label.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #664d03; -fx-padding: 8;"
                + "-fx-background-radius: 4;");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static VBox headline(String labelText, String value) {
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: gray;");
        Label big = wrapping(value);
        big.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        return new VBox(2, label, big);
    }

    private GridPane rowsGrid(List<PreviewDetails.Row> rows) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(Region.USE_PREF_SIZE);   // labels never get squeezed into "..."
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values, new ColumnConstraints());

        int line = 0;
        for (PreviewDetails.Row row : rows) {
            Label name = new Label(row.label() + ":");
            name.setStyle("-fx-text-fill: gray;");
            Label value = wrapping(row.secret() ? row.maskedValue() : row.value());
            grid.add(name, 0, line);
            grid.add(value, 1, line);
            if (row.secret()) {
                ToggleButton show = new ToggleButton("Show");
                show.setOnAction(e -> {
                    value.setText(show.isSelected() ? row.value() : row.maskedValue());
                    show.setText(show.isSelected() ? "Hide" : "Show");
                    stage.sizeToScene();
                });
                guarded.add(show);   // a stray click must not reveal a password either
                grid.add(show, 2, line);
            }
            line++;
        }
        return grid;
    }

    private static VBox checks(List<Indicator> indicators, String summary) {
        Label title = new Label("Address checks");
        title.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(4, title);
        for (Indicator indicator : indicators) {
            Label label = wrapping(symbol(indicator.level()) + "  " + indicator.message());
            label.setStyle("-fx-text-fill: " + colour(indicator.level()) + ";");
            box.getChildren().add(label);
        }
        if (!summary.isEmpty()) {
            Label note = wrapping(summary);
            note.setStyle("-fx-font-style: italic;");
            box.getChildren().add(note);
        }
        return box;
    }

    /** What the redirect check found (already worded and sanitized by PreviewModel). */
    private static VBox redirectBox(List<String> lines) {
        Label title = new Label("Where this goes");
        title.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(4, title);
        for (String line : lines) {
            box.getChildren().add(wrapping(line));
        }
        return box;
    }

    /** Warnings that belong to the buttons, e.g. "Copying includes the password". */
    private static VBox cautions(PreviewModel model) {
        VBox box = new VBox(2);
        if (model.canCheckRedirects()) {
            // Checking is a real visit: a tracking link learns that someone scanned it.
            Label note = wrapping("\"Check where this goes\" contacts the site. It will see a visit from "
                    + "your computer. Nothing opens in your browser.");
            note.setStyle("-fx-text-fill: gray;");
            box.getChildren().add(note);
        }
        for (Action action : model.actions()) {
            if (!action.caution().isEmpty()) {
                Label label = wrapping("⚠ " + action.caution());
                label.setStyle("-fx-text-fill: #8a5300;");
                box.getChildren().add(label);
            }
        }
        box.setManaged(!box.getChildren().isEmpty());   // no empty gap when there's nothing to say
        return box;
    }

    /**
     * Two rows: the actions on top (they wrap onto a new line if there are many),
     * then "Select area" on the left and Cancel on the right.
     */
    private VBox buttons(PreviewModel model) {
        FlowPane actionRow = new FlowPane(8, 8);
        actionRow.setAlignment(Pos.CENTER_RIGHT);
        for (Action action : model.actions()) {
            Button button = new Button(action.label());
            button.setOnAction(e -> finish(new PreviewChoice.RunAction(action)));
            guarded.add(button);
            actionRow.getChildren().add(button);
        }

        HBox bottomRow = new HBox(8);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        if (model.canSelectArea()) {
            Button selectArea = new Button("Not this one? Select area");
            selectArea.setOnAction(e -> finish(new PreviewChoice.SelectArea()));
            guarded.add(selectArea);
            bottomRow.getChildren().add(selectArea);
        }
        if (model.canCheckRedirects()) {
            Button check = new Button("Check where this goes");
            check.setOnAction(e -> startChecking(check));
            guarded.add(check);   // it uses the network, so it gets the same protection as an action
            bottomRow.getChildren().add(check);
        }
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        bottomRow.getChildren().add(gap);

        // Cancel is never guarded: closing is always safe.
        Button cancel = new Button("Cancel");
        cancel.setDefaultButton(true);   // Enter = Cancel
        cancel.setCancelButton(true);    // Esc = Cancel
        cancel.setOnAction(e -> finish(new PreviewChoice.Cancel()));
        bottomRow.getChildren().add(cancel);

        return new VBox(8, actionRow, bottomRow);
    }

    private static Label wrapping(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);   // grow downwards instead of cutting text off with "..."
        return label;
    }

    private static String symbol(Indicator.Level level) {
        return switch (level) {
            case DANGER -> "⛔";   // no-entry sign
            case WARN -> "⚠";     // warning sign
            case INFO -> "ℹ";     // information
            case OK -> "✓";       // tick
        };
    }

    private static String colour(Indicator.Level level) {
        return switch (level) {
            case DANGER -> "#b02a37";
            case WARN -> "#8a5300";
            case INFO -> "#444444";
            case OK -> "#1e7b34";
        };
    }

    // --- Size and position ---

    /**
     * Makes the window exactly tall enough for its content, up to a limit; beyond that it scrolls.
     * JavaFX only knows how tall wrapped text is AFTER css and layout have run, so we run them first.
     */
    private void fitToContent(VBox root, VBox body, ScrollPane scroll) {
        root.applyCss();
        root.layout();
        double bodyWidth = WIDTH - root.getPadding().getLeft() - root.getPadding().getRight() - 20;
        double bodyHeight = body.prefHeight(bodyWidth);
        scroll.setPrefViewportHeight(Math.min(bodyHeight, screen.getHeight() * MAX_SCREEN_SHARE));
        stage.sizeToScene();
    }

    /** After an update the window may be taller: move it up if its bottom would leave the screen. */
    private void keepOnScreen() {
        double overflow = stage.getY() + stage.getHeight() - screen.getMaxY();
        if (overflow > 0) {
            stage.setY(Math.max(screen.getMinY(), stage.getY() - overflow));
        }
    }

    private static Screen screenUnderMouse() {
        Point2D mouse = new Robot().getMousePosition();
        return Screen.getScreens().stream()
                .filter(s -> s.getBounds().contains(mouse))
                .findFirst()
                .orElse(Screen.getPrimary());
    }

    // --- Showing and ending ---

    private void show() {
        stage.show();
        stage.toFront();
        startGuard();   // starts once the window is actually visible
    }

    /** Disables every guarded button, then enables them again after INPUT_GUARD. */
    private void startGuard() {
        if (guard != null) {
            guard.stop();   // an older guard must not enable buttons at the wrong moment
        }
        guarded.forEach(b -> b.setDisable(true));
        guard = new PauseTransition(INPUT_GUARD);
        guard.setOnFinished(e -> guarded.forEach(b -> b.setDisable(false)));
        guard.play();
    }

    /**
     * "Check where this goes": the window STAYS open and says it's checking. Every button except
     * Cancel is disabled, so nothing can be opened or copied while the answer is on its way.
     */
    private void startChecking(Button check) {
        if (guard != null) {
            guard.stop();
        }
        guarded.forEach(b -> b.setDisable(true));
        check.setText("Checking…");
        round.complete(new PreviewChoice.CheckRedirects());
    }

    /** Closes the window FIRST, then reports the choice. Runs only once, however it's triggered. */
    private void finish(PreviewChoice choice) {
        if (finished) {
            return;
        }
        finished = true;
        if (current == this) {
            current = null;
        }
        if (guard != null) {
            guard.stop();
        }
        stage.close();   // fires setOnHidden -> finish(Cancel), which returns at once (already finished)
        // If a check is running, this round is already complete: the workflow will get Cancel
        // from its next update() instead, because 'current' is now null.
        round.complete(choice);
    }
}
