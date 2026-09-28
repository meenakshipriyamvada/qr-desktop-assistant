package com.qrassistant.ui;

import com.qrassistant.capture.CapturedScreen;
import com.qrassistant.scan.DecodedQr;
import com.qrassistant.scan.ImagePoint;
import com.qrassistant.scan.QrGeometry;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The full-screen "frozen screen" window.
 *
 *  - Shows the screenshot, slightly dimmed, exactly where the real screen was.
 *  - PICK mode: draws a numbered outline around every detected code; clicking one picks it.
 *  - In every mode: dragging draws a box; Esc or right-click cancels.
 *
 * It contains no scanning logic. It reports ONE OverlayResult and closes itself.
 * All methods run on the JavaFX thread.
 */
public final class SelectionOverlay {

    /** Boxes smaller than this (in overlay pixels) are treated as a stray click, not a selection. */
    private static final double MIN_BOX_SIZE = 8;

    /** The overlay currently on screen, if any (FX thread only). Used to close it in emergencies. */
    private static SelectionOverlay current;

    private final CapturedScreen captured;
    private final Stage stage = new Stage(StageStyle.UNDECORATED);
    private final ImageView imageView;
    private final Rectangle selectionBox = new Rectangle();
    private final CompletableFuture<OverlayResult> result = new CompletableFuture<>();

    private boolean finished;
    private boolean dragging;
    private double dragStartX;
    private double dragStartY;

    /**
     * Opens the overlay and returns immediately.
     * The returned future is completed (exactly once) when the user picks, drags or cancels.
     */
    public static CompletableFuture<OverlayResult> open(
            CapturedScreen captured, List<DecodedQr> codes, OverlayMode mode) {
        closeAny();   // never two overlays at once
        SelectionOverlay overlay = new SelectionOverlay(captured, codes, mode);
        current = overlay;
        overlay.showOnTop();
        return overlay.result;
    }

    /** Emergency exit: closes any open overlay as "cancelled". Safe to call when none is open. (I1) */
    public static void closeAny() {
        if (current != null) {
            current.finish(new OverlayResult.Cancelled());
        }
    }

    private SelectionOverlay(CapturedScreen captured, List<DecodedQr> codes, OverlayMode mode) {
        this.captured = captured;
        java.awt.Rectangle bounds = captured.screenBounds();   // logical pixels

        // 1. The frozen screenshot, stretched to the monitor's LOGICAL size.
        //    On a 150% display JavaFX draws it with 1.5x the pixels, i.e. at full sharpness.
        imageView = new ImageView(ImageConverter.toFxImage(captured.image()));
        imageView.setFitWidth(bounds.width);
        imageView.setFitHeight(bounds.height);

        // 2. A see-through dark layer, so it's obvious the screen is "frozen".
        Rectangle dim = new Rectangle(bounds.width, bounds.height, Color.rgb(0, 0, 0, 0.35));

        // 3. Outlines + the drag box, positioned freely inside a Pane.
        Pane shapes = new Pane();
        for (int i = 0; i < codes.size(); i++) {
            Group outline = createOutline(codes.get(i), i + 1);
            if (outline != null) {
                shapes.getChildren().add(outline);
            }
        }
        selectionBox.setFill(Color.rgb(255, 255, 255, 0.12));
        selectionBox.setStroke(Color.WHITE);
        selectionBox.setStrokeWidth(2);
        selectionBox.getStrokeDashArray().setAll(8.0, 6.0);
        selectionBox.setVisible(false);
        selectionBox.setMouseTransparent(true);
        shapes.getChildren().add(selectionBox);

        // 4. The instruction banner at the top. Mouse-transparent, so it never blocks dragging.
        Label banner = new Label(mode.instruction());
        banner.setStyle("-fx-background-color: rgba(20,20,20,0.85); -fx-text-fill: white;"
                + "-fx-font-size: 15px; -fx-padding: 10 18; -fx-background-radius: 8;");
        banner.setMouseTransparent(true);

        StackPane root = new StackPane(imageView, dim, shapes, banner);
        StackPane.setAlignment(banner, Pos.TOP_CENTER);
        StackPane.setMargin(banner, new Insets(24));

        root.setOnMousePressed(this::onMousePressed);
        root.setOnMouseDragged(this::onMouseDragged);
        root.setOnMouseReleased(this::onMouseReleased);

        Scene scene = new Scene(root, bounds.width, bounds.height, Color.BLACK);
        scene.setCursor(Cursor.CROSSHAIR);
        // An event FILTER runs before anything else gets the key, so Esc always works. (I1, I2)
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                finish(new OverlayResult.Cancelled());
            }
        });

        stage.setScene(scene);
        stage.setX(bounds.x);
        stage.setY(bounds.y);
        stage.setAlwaysOnTop(true);
        // Closed some other way (e.g. Alt+F4)? Count it as cancelled, so the workflow never waits forever.
        stage.setOnHidden(event -> finish(new OverlayResult.Cancelled()));
    }

    private void showOnTop() {
        stage.show();
        stage.toFront();
        stage.requestFocus();   // needed so Esc reaches us (I2)
    }

    // --- Outlines (PICK mode) ---

    /** A clickable box + number badge around one code, or null if its position is unknown. */
    private Group createOutline(DecodedQr code, int number) {
        List<ImagePoint> corners = QrGeometry.estimateCorners(code);
        if (corners.isEmpty()) {
            return null;
        }

        Polygon box = new Polygon();
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        for (ImagePoint corner : corners) {
            // Image pixels -> overlay position, via CapturedScreen (the only place this maths lives).
            double x = captured.toOverlayX(corner.x());
            double y = captured.toOverlayY(corner.y());
            box.getPoints().addAll(x, y);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
        }
        box.setFill(Color.rgb(0, 160, 255, 0.18));
        box.setStroke(Color.rgb(0, 160, 255));
        box.setStrokeWidth(3);

        Label badge = new Label(String.valueOf(number));
        badge.setStyle("-fx-background-color: rgb(0,160,255); -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-font-size: 14px; -fx-padding: 2 8; -fx-background-radius: 10;");
        badge.relocate(minX - 10, minY - 12);

        Group outline = new Group(box, badge);
        outline.setCursor(Cursor.HAND);
        outline.setOnMouseEntered(e -> box.setStroke(Color.YELLOW));
        outline.setOnMouseExited(e -> box.setStroke(Color.rgb(0, 160, 255)));
        outline.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                finish(new OverlayResult.Cancelled());
            }
            e.consume();   // don't let the background start a drag box
        });
        outline.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                finish(new OverlayResult.Picked(code));
            }
            e.consume();
        });
        return outline;
    }

    // --- Dragging a box (all modes) ---

    private void onMousePressed(MouseEvent e) {
        if (e.getButton() == MouseButton.SECONDARY) {
            finish(new OverlayResult.Cancelled());
            return;
        }
        if (e.getButton() == MouseButton.PRIMARY) {
            dragging = true;
            dragStartX = e.getX();
            dragStartY = e.getY();
            updateSelectionBox(e.getX(), e.getY());
            selectionBox.setVisible(true);
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (dragging) {
            updateSelectionBox(e.getX(), e.getY());
        }
    }

    private void onMouseReleased(MouseEvent e) {
        if (!dragging || e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        dragging = false;
        double width = Math.abs(e.getX() - dragStartX);
        double height = Math.abs(e.getY() - dragStartY);
        if (width < MIN_BOX_SIZE || height < MIN_BOX_SIZE) {
            selectionBox.setVisible(false);   // just a click on empty space: ignore it
            return;
        }
        java.awt.Rectangle area = captured.overlayBoxToImageArea(dragStartX, dragStartY, e.getX(), e.getY());
        if (area.isEmpty()) {
            selectionBox.setVisible(false);
            return;
        }
        finish(new OverlayResult.Region(area));
    }

    /** Works whichever direction you drag in (up-left, down-right, ...). */
    private void updateSelectionBox(double x, double y) {
        selectionBox.setX(Math.min(dragStartX, x));
        selectionBox.setY(Math.min(dragStartY, y));
        selectionBox.setWidth(Math.abs(x - dragStartX));
        selectionBox.setHeight(Math.abs(y - dragStartY));
    }

    // --- Ending ---

    /** Closes the overlay FIRST, then reports the result. Runs only once, however it's triggered. */
    private void finish(OverlayResult outcome) {
        if (finished) {
            return;
        }
        finished = true;
        if (current == this) {
            current = null;
        }
        stage.close();
        imageView.setImage(null);   // let go of the screenshot's memory (S7)
        result.complete(outcome);
    }
}