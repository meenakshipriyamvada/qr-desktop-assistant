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
    private final CompletableFuture<PreviewChoice> result = new CompletableFuture<>();
    /** Buttons that stay disabled until the input guard ends. */
    private final List<ButtonBase> guarded = new ArrayList<>();
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
        return window.result;
    }

    /** Emergency exit: closes any open preview as "Cancel". Safe to call when none is open. */
    public static void closeAny() {
        if (current != null) {
            current.finish(new PreviewChoice.Cancel());
        }
    }

    private PreviewWindow(PreviewModel model) {
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

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Label kind = new Label(model.kind());
        kind.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        VBox root = new VBox(10, kind, scroll, cautions(model.actions()), buttons(model));
        root.setPadding(new Insets(14));
        root.setPrefWidth(WIDTH);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        stage.setTitle("QR code — " + model.kind());
        stage.setScene(new Scene(root));
        stage.setAlwaysOnTop(true);   // may be opened by the shortcut while other apps are in front
        stage.setOnHidden(event -> finish(new PreviewChoice.Cancel()));   // the X button = Cancel

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

    /** Warnings that belong to the action buttons, e.g. "Copying includes the password". */
    private static VBox cautions(List<Action> actions) {
        VBox box = new VBox(2);
        for (Action action : actions) {
            if (!action.caution().isEmpty()) {
                Label label = wrapping("⚠ " + action.caution());
                label.setStyle("-fx-text-fill: #8a5300;");
                box.getChildren().add(label);
            }
        }
        box.setManaged(!box.getChildren().isEmpty());   // no empty gap when there's nothing to say
        return box;
    }

    private HBox buttons(PreviewModel model) {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_RIGHT);

        if (model.canSelectArea()) {
            Button selectArea = new Button("Not this one? Select area");
            selectArea.setOnAction(e -> finish(new PreviewChoice.SelectArea()));
            guarded.add(selectArea);
            bar.getChildren().add(selectArea);
        }

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        bar.getChildren().add(gap);

        for (Action action : model.actions()) {
            Button button = new Button(action.label());
            button.setOnAction(e -> finish(new PreviewChoice.RunAction(action)));
            guarded.add(button);
            bar.getChildren().add(button);
        }

        // Cancel is never guarded: closing is always safe.
        Button cancel = new Button("Cancel");
        cancel.setDefaultButton(true);   // Enter = Cancel
        cancel.setCancelButton(true);    // Esc = Cancel
        cancel.setOnAction(e -> finish(new PreviewChoice.Cancel()));
        bar.getChildren().add(cancel);
        return bar;
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
        Rectangle2D screen = screenUnderMouse().getVisualBounds();

        root.applyCss();
        root.layout();
        double bodyWidth = WIDTH - root.getPadding().getLeft() - root.getPadding().getRight() - 20;
        double bodyHeight = body.prefHeight(bodyWidth);
        scroll.setPrefViewportHeight(Math.min(bodyHeight, screen.getHeight() * MAX_SCREEN_SHARE));
        stage.sizeToScene();

        // Centre on the monitor under the mouse: the one the user just scanned.
        stage.setOnShown(e -> {
            stage.setX(screen.getMinX() + (screen.getWidth() - stage.getWidth()) / 2);
            stage.setY(screen.getMinY() + (screen.getHeight() - stage.getHeight()) / 2);
        });
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
        guarded.forEach(b -> b.setDisable(true));
        stage.show();
        stage.toFront();

        // The guard starts once the window is actually visible.
        PauseTransition guard = new PauseTransition(INPUT_GUARD);
        guard.setOnFinished(e -> guarded.forEach(b -> b.setDisable(false)));
        guard.play();
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
        stage.close();   // fires setOnHidden -> finish(Cancel), which returns at once (already finished)
        result.complete(choice);
    }
}
