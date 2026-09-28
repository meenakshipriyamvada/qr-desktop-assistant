package com.qrassistant.ui;

import com.qrassistant.capture.CapturedScreen;
import com.qrassistant.scan.DecodedQr;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Rectangle;
import java.util.List;

/**
 * TEMPORARY (steps 3-5): shows a captured screenshot, its sizes and what the scanner found,
 * so we can check that capture, display scaling and decoding work.
 * Replaced by the real SelectionOverlay in step 6.
 */
public final class CaptureDebugWindow {

    private CaptureDebugWindow() {
    }

    /** Longest QR text shown here; the real preview (step 10) handles long text properly. */
    private static final int MAX_SHOWN_LENGTH = 200;

    public static void show(CapturedScreen captured, List<DecodedQr> codes) {
        Rectangle bounds = captured.screenBounds();

        ImageView imageView = new ImageView(ImageConverter.toFxImage(captured.image()));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(800);        // shrink to fit the window; the image itself stays full size

        Label logical = new Label(String.format(
                "Monitor (logical pixels): %d x %d at (%d, %d)",
                bounds.width, bounds.height, bounds.x, bounds.y));
        Label physical = new Label(String.format(
                "Captured image (physical pixels): %d x %d",
                captured.image().getWidth(), captured.image().getHeight()));
        Label scale = new Label(String.format(
                "Scale: %.2f x %.2f   (compare with Windows Settings > Display > Scale)",
                captured.scaleX(), captured.scaleY()));

        Label result = new Label(describe(codes));
        result.setWrapText(true);
        result.setMaxWidth(800);
        result.setStyle("-fx-font-weight: bold;");

        VBox root = new VBox(8, logical, physical, scale, result, imageView);
        root.setPadding(new Insets(12));

        Stage stage = new Stage();
        stage.setTitle("Capture test");
        stage.setScene(new Scene(root));

        // When the window closes, let go of the screenshot so the memory can be freed. (S7)
        stage.setOnHidden(event -> imageView.setImage(null));

        stage.show();
    }

    /*
     * WARNING: QR text is untrusted. This debug window shows it raw (shortened).
     * The real preview (step 10) passes it through DisplaySanitizer first (X1).
     */
    private static String describe(List<DecodedQr> codes) {
        if (codes.isEmpty()) {
            return "No QR code found.";
        }
        StringBuilder text = new StringBuilder(codes.size() + " QR code(s) found:");
        for (int i = 0; i < codes.size(); i++) {
            DecodedQr code = codes.get(i);
            text.append("\nQR ").append(i + 1).append(" \u2192 ").append(shorten(code.text()))
                    .append("   (").append(code.points().size()).append(" location points)");
        }
        return text.toString();
    }

    private static String shorten(String text) {
        return text.length() > MAX_SHOWN_LENGTH
                ? text.substring(0, MAX_SHOWN_LENGTH) + "\u2026"
                : text;
    }
}