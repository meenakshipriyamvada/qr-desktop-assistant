package com.qrassistant.ui;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The "Open image" dialog. Runs on the JavaFX thread only.
 *
 * "All files" is offered on purpose: if someone picks a WebP or HEIC file,
 * the loader can explain why it doesn't work, instead of the file just being invisible.
 */
public final class ImageFileChooser {

    /** The folder used last time, so the dialog opens there again (this run of the app only). */
    private static File lastFolder;

    private ImageFileChooser() {
    }

    /**
     * Shows the dialog and waits for the user.
     *
     * @param owner the window to keep it in front of, or null
     * @return the chosen file, or empty if the user cancelled
     */
    public static Optional<Path> choose(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an image with a QR code");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif",
                        "*.tif", "*.tiff"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        if (lastFolder != null && lastFolder.isDirectory()) {
            chooser.setInitialDirectory(lastFolder);
        }

        File file = chooser.showOpenDialog(owner);   // waits until the user picks or cancels
        if (file == null) {
            return Optional.empty();
        }
        lastFolder = file.getParentFile();
        return Optional.of(file.toPath());
    }
}
