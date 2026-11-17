package com.qrassistant.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * "This image has several QR codes. Which one?" A simple list.
 *
 * Used for image files, where there is no live screen to click on.
 * Like the other windows it makes no decisions: it gets ready-made (sanitized) labels
 * and reports the chosen position, or -1 for Cancel. Runs on the JavaFX thread only.
 */
public final class QrChooserDialog {

    public static final int CANCELLED = -1;

    /** The dialog currently open, if any. Used to close it in emergencies. */
    private static QrChooserDialog current;

    private final Stage stage = new Stage();
    private final CompletableFuture<Integer> result = new CompletableFuture<>();
    private boolean finished;

    /**
     * Opens the list and returns at once. The future gets the chosen index, or CANCELLED.
     *
     * @param labels one line per code, already sanitized, e.g. "Web address: https://example.com"
     */
    public static CompletableFuture<Integer> open(List<String> labels) {
        closeAny();
        QrChooserDialog dialog = new QrChooserDialog(labels);
        current = dialog;
        dialog.stage.show();
        dialog.stage.toFront();
        return dialog.result;
    }

    /** Emergency exit: closes any open list as "cancelled". Safe to call when none is open. */
    public static void closeAny() {
        if (current != null) {
            current.finish(CANCELLED);
        }
    }

    private QrChooserDialog(List<String> labels) {
        Label question = new Label("This image has " + labels.size() + " QR codes. Which one do you want?");
        question.setWrapText(true);

        ListView<String> list = new ListView<>();
        for (int i = 0; i < labels.size(); i++) {
            list.getItems().add((i + 1) + ".  " + labels.get(i));
        }
        list.setPrefHeight(Math.min(10, labels.size()) * 26 + 4);
        list.getSelectionModel().selectFirst();

        Button use = new Button("Use this one");
        use.setOnAction(e -> finish(list.getSelectionModel().getSelectedIndex()));
        // Double-click picks too. Nothing risky happens: the preview still asks before any action.
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                finish(list.getSelectionModel().getSelectedIndex());
            }
        });

        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);   // Esc = Cancel
        cancel.setOnAction(e -> finish(CANCELLED));

        HBox buttons = new HBox(8, use, cancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, question, list, buttons);
        root.setPadding(new Insets(14));
        root.setPrefWidth(480);

        stage.setTitle("Choose a QR code");
        stage.setScene(new Scene(root));
        stage.setAlwaysOnTop(true);
        stage.setOnHidden(e -> finish(CANCELLED));   // the X button = Cancel
    }

    /** Closes the window FIRST, then reports. Runs only once, however it's triggered. */
    private void finish(int index) {
        if (finished) {
            return;
        }
        finished = true;
        if (current == this) {
            current = null;
        }
        stage.close();
        result.complete(index);
    }
}
