package com.qrassistant.ui;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * The app's icon in the Windows notification area ("system tray"), with its menu:
 * Scan screen, Scan image file..., Open window, Quit.
 *
 * Uses AWT, because JavaFX has no tray support. AWT has its OWN UI thread (the "event dispatch
 * thread"), so the menu's callbacks arrive there: whoever creates the tray must make them
 * thread-safe (hand work to the scan worker, or to the JavaFX thread with Platform.runLater).
 */
public final class AppTray {

    private static final Logger LOG = Logger.getLogger(AppTray.class.getName());

    private final TrayIcon icon;
    /** Show the "still running" balloon only the first time the window is closed. */
    private final AtomicBoolean toldAboutTray = new AtomicBoolean(false);

    private AppTray(TrayIcon icon) {
        this.icon = icon;
    }

    /**
     * Adds the tray icon.
     *
     * @return the tray, or empty if Windows doesn't offer a tray (then the window's X must quit)
     */
    public static Optional<AppTray> install(Runnable onScanScreen, Runnable onScanFile,
                                            Runnable onOpenWindow, Runnable onQuit) {
        try {
            if (!SystemTray.isSupported()) {
                return Optional.empty();
            }
            PopupMenu menu = new PopupMenu();
            menu.add(item("Scan screen", onScanScreen));
            menu.add(item("Scan image file…", onScanFile));
            menu.add(item("Open window", onOpenWindow));
            menu.addSeparator();
            menu.add(item("Quit", onQuit));

            TrayIcon icon = new TrayIcon(drawIcon(), "QR Desktop Assistant (Ctrl+Shift+Q)", menu);
            icon.setImageAutoSize(true);                       // Windows picks the size (HiDPI)
            icon.addActionListener(e -> onOpenWindow.run());   // double-click the icon = open window
            SystemTray.getSystemTray().add(icon);
            return Optional.of(new AppTray(icon));
        } catch (AWTException | RuntimeException e) {
            LOG.warning("Tray icon unavailable: " + e.getClass().getName());
            return Optional.empty();
        }
    }

    /** A small balloon: "Still running here". Only the first time it's called. */
    public void tellStillRunning() {
        if (toldAboutTray.compareAndSet(false, true)) {
            EventQueue.invokeLater(() -> icon.displayMessage("QR Desktop Assistant is still running",
                    "Press Ctrl+Shift+Q to scan, or right-click this icon. Choose Quit to exit.",
                    TrayIcon.MessageType.INFO));
        }
    }

    /** Removes the icon. Called once when the app quits; otherwise the icon lingers until hovered. */
    public void remove() {
        try {
            SystemTray.getSystemTray().remove(icon);
        } catch (RuntimeException e) {
            LOG.warning("Couldn't remove tray icon: " + e.getClass().getName());
        }
    }

    private static MenuItem item(String label, Runnable onClick) {
        MenuItem item = new MenuItem(label);
        item.addActionListener(e -> onClick.run());
        return item;
    }

    /** Draws a tiny QR-like icon in code, so the app needs no image files. */
    private static BufferedImage drawIcon() {
        int size = 64;   // drawn large; Windows scales it down sharply
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(Color.WHITE);
        g.fillRoundRect(0, 0, size, size, 12, 12);
        g.setColor(new Color(0x1f2937));
        // Three "finder" squares, like the corners of a real QR code.
        for (int[] corner : new int[][]{{6, 6}, {38, 6}, {6, 38}}) {
            g.fillRect(corner[0], corner[1], 20, 20);
            g.setColor(Color.WHITE);
            g.fillRect(corner[0] + 4, corner[1] + 4, 12, 12);
            g.setColor(new Color(0x1f2937));
            g.fillRect(corner[0] + 7, corner[1] + 7, 6, 6);
        }
        // A few "data" dots in the last corner.
        g.fillRect(40, 40, 8, 8);
        g.fillRect(50, 50, 8, 8);
        g.fillRect(50, 38, 6, 6);
        g.dispose();
        return image;
    }
}
