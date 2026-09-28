package com.qrassistant.ui;

import com.qrassistant.content.QrContent;
import com.qrassistant.system.ClipboardService;
import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.UrlReport;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

/**
 * TEMPORARY (until step 10): shows what the chosen QR code was recognized as, with a Copy button.
 * Replaced by the real preview window.
 *
 * Text is shown through DisplaySanitizer, so hidden characters are visible. (X1)
 * Copy puts the ORIGINAL text on the clipboard.
 */
public final class ResultDebugDialog {

    private static final int MAX_SHOWN_LENGTH = 1200;
    private static final ButtonType COPY = new ButtonType("Copy", ButtonBar.ButtonData.LEFT);

    private ResultDebugDialog() {
    }

    public static void show(QrContent content, UrlReport report, ClipboardService clipboard) {
        // A record's toString() lists all its fields - handy while testing.
        String text = content.toString();
        if (report != null) {
            StringBuilder checks = new StringBuilder("\n\nURL checks (can open: " + report.canOpen() + "):");
            for (Indicator indicator : report.indicators()) {
                checks.append("\n[").append(indicator.level()).append("] ").append(indicator.message());
            }
            text = text + checks;
        }
        DisplaySanitizer.SanitizedText details = DisplaySanitizer.forMultiLine(text);
        String shown = details.text();
        if (shown.length() > MAX_SHOWN_LENGTH) {
            shown = shown.substring(0, MAX_SHOWN_LENGTH) + "\u2026";
        }
        if (details.hadHiddenCharacters()) {
            shown = "\u26A0 This text contains hidden characters that can disguise it (shown as \u27E6U+\u2026\u27E7).\n\n"
                    + shown;
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION, shown, COPY, ButtonType.CLOSE);
        alert.setTitle("QR code (test)");
        alert.setHeaderText("Recognized as: " + content.getClass().getSimpleName());
        alert.setResultConverter(button -> {
            if (button == COPY) {
                clipboard.copy(content.rawText());   // the original, not the sanitized text
            }
            return button;
        });
        alert.show();   // not showAndWait: the scan can finish while this stays open
    }
}