package com.qrassistant.ui;

/**
 * Makes untrusted text SAFE TO LOOK AT, by turning hidden characters into visible markers. (X1)
 *
 * QR text can contain characters you can't see, which can make it look like something else:
 *  - "right-to-left override" (U+202E) makes the rest of the text display BACKWARDS:
 *        stored:  invoice[U+202E]fdp.exe      shown: invoiceexe.pdf
 *  - zero-width characters make "pay[invisible]pal.com" look exactly like "paypal.com"
 *  - control characters and line breaks can mess up the layout, e.g. fake an extra
 *    "Security: OK" line in the preview
 *
 * This class only changes what is DISPLAYED. "Copy" always uses the original text.
 * Pure logic (no JavaFX), so it has automatic tests.
 */
public final class DisplaySanitizer {

    /** The result: text that's safe to show, and whether anything had to be revealed. */
    public record SanitizedText(String text, boolean hadHiddenCharacters) {
    }

    private DisplaySanitizer() {
    }

    /**
     * For longer text shown in a box (notes, descriptions): line breaks and tabs are kept.
     * Windows-style line breaks (\r\n) and old Mac ones (\r) become a normal \n.
     */
    public static SanitizedText forMultiLine(String raw) {
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        return reveal(normalized, true);
    }

    /**
     * For one-line values (a web address, a network name, a phone number):
     * line breaks and tabs are revealed too, because they have no business being there.
     */
    public static SanitizedText forSingleLine(String raw) {
        return reveal(raw, false);
    }

    private static SanitizedText reveal(String text, boolean allowLineBreaks) {
        StringBuilder result = new StringBuilder(text.length());
        boolean revealedSomething = false;

        // codePoints() instead of chars(): some hidden characters (e.g. U+E0041) are too big
        // for a single Java char and are stored as TWO chars. Code points handle them as one.
        for (int cp : text.codePoints().toArray()) {
            boolean isLineBreakOrTab = cp == '\n' || cp == '\t';
            if (isLineBreakOrTab && allowLineBreaks) {
                result.append((char) cp);
            } else if (isLineBreakOrTab || isHidden(cp)) {
                result.append(String.format("\u27E6U+%04X\u27E7", cp));   // e.g. "U+202E" between special brackets
                revealedSomething = true;
            } else {
                result.appendCodePoint(cp);
            }
        }
        return new SanitizedText(result.toString(), revealedSomething);
    }

    /** Characters that are invisible or change how the text around them is displayed. */
    private static boolean isHidden(int cp) {
        // Allowed on purpose: zero-width (non-)joiners. Hindi, Malayalam and other Indian scripts
        // need them to write some letters correctly, and family/profession emoji are built with them.
        if (cp == 0x200C || cp == 0x200D) {
            return false;
        }
        int type = Character.getType(cp);
        return type == Character.CONTROL                 // old "command" characters, e.g. U+0007 bell
                || type == Character.FORMAT              // invisible formatting: U+202E, U+200B, U+FEFF...
                || type == Character.LINE_SEPARATOR      // U+2028: a line break in disguise
                || type == Character.PARAGRAPH_SEPARATOR; // U+2029: same
    }
}