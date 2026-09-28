package com.qrassistant.urlcheck;

/**
 * One finding about a URL, e.g. "Uses HTTPS" or "The address hides a username before '@'".
 *
 * Indicators are FACTS for the user to weigh. The app never adds them up into a "safe" verdict:
 * a phishing site can have perfect HTTPS and a normal-looking name. (X2)
 *
 * The message may include parts of the URL, which is UNTRUSTED: show it through DisplaySanitizer.
 */
public record Indicator(Level level, String message) {

    /** Most serious first; reports are sorted in this order. */
    public enum Level {
        DANGER,   // a strong sign of a trick
        WARN,     // worth thinking twice about
        INFO,     // good to know
        OK        // a reassuring fact (never a guarantee)
    }
}