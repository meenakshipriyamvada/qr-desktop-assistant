package com.qrassistant.system;

import com.melloware.jintellitype.HotkeyListener;
import com.melloware.jintellitype.JIntellitype;

import java.util.logging.Logger;

/**
 * Ctrl+Shift+Q as a global shortcut, using JIntellitype (a small native Windows library;
 * its DLL is inside the jar and loaded automatically).
 *
 * The ONLY class that imports JIntellitype.
 */
public class JIntellitypeShortcut implements GlobalShortcut {

    private static final Logger LOG = Logger.getLogger(JIntellitypeShortcut.class.getName());

    /** Our own number for the shortcut; JIntellitype passes it back when it's pressed. */
    private static final int SCAN_SHORTCUT_ID = 1;

    private JIntellitype library;     // null until registered
    private HotkeyListener listener;

    @Override
    public boolean register(Runnable onPressed) {
        try {
            if (!JIntellitype.isJIntellitypeSupported()) {
                return false;   // not Windows, or the DLL couldn't be loaded
            }
            library = JIntellitype.getInstance();
            // Called on JIntellitype's own thread. onPressed only hands work to the scan worker,
            // so it returns at once and never blocks the native message loop.
            listener = id -> {
                if (id == SCAN_SHORTCUT_ID) {
                    onPressed.run();
                }
            };
            library.addHotKeyListener(listener);
            library.registerHotKey(SCAN_SHORTCUT_ID,
                    JIntellitype.MOD_CONTROL + JIntellitype.MOD_SHIFT, 'Q');
            return true;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            // JIntellitypeException is a RuntimeException. UnsatisfiedLinkError: the DLL failed to load.
            LOG.warning("Global shortcut unavailable: " + e.getClass().getName());
            unregister();
            return false;
        }
    }

    @Override
    public void unregister() {
        if (library == null) {
            return;
        }
        try {
            library.unregisterHotKey(SCAN_SHORTCUT_ID);
            if (listener != null) {
                library.removeHotKeyListener(listener);
            }
            library.cleanUp();   // stops JIntellitype's native thread so the app can exit
        } catch (RuntimeException e) {
            LOG.warning("Couldn't remove the global shortcut: " + e.getClass().getName());
        } finally {
            library = null;
            listener = null;
        }
    }
}
