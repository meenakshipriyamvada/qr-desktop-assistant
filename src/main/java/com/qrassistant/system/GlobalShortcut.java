package com.qrassistant.system;

/**
 * A keyboard shortcut that works everywhere in Windows, even while another app is in front.
 *
 * An interface, so the rest of the app doesn't depend on the library behind it
 * (JIntellitype today), and a fake can stand in for it.
 */
public interface GlobalShortcut {

    /**
     * Starts listening for the shortcut.
     *
     * @param onPressed called on a background thread each time the shortcut is pressed.
     *                  It must return quickly and must not touch JavaFX directly.
     * @return false if the shortcut could NOT be set up. True means "no error was reported";
     *         Windows doesn't always report it when another app already owns the same keys.
     */
    boolean register(Runnable onPressed);

    /** Stops listening and frees the native resources. Safe to call even if register failed. */
    void unregister();
}
