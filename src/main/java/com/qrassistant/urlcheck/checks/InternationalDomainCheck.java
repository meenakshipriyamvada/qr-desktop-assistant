package com.qrassistant.urlcheck.checks;

import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.Indicator.Level;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.UrlCheck;

import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * International (non-English) letters in the site name. (U7)
 *
 * Perfectly normal for many sites (German, Hindi or Chinese names), but also the basis of
 * "look-alike" tricks: an address with a Cyrillic "a" looks exactly like apple.com.
 *
 *  - any international letters        -> WARN, showing the name BOTH ways: as read, and as "xn--..."
 *  - Latin mixed with Cyrillic/Greek  -> DANGER: honest names almost never mix these alphabets
 *  - made ONLY of letters that imitate English ones                                  -> DANGER
 *
 * Honest limit: look-alikes made of plain English letters ("paypa1.com", "rn" vs "m") can't be
 * detected reliably without a huge list of real brands - so this check doesn't pretend to.
 */
public class InternationalDomainCheck implements UrlCheck {

    /** Alphabets that contain letters which look like English ones. */
    private static final Set<UnicodeScript> CONFUSABLE_SCRIPTS =
            EnumSet.of(UnicodeScript.LATIN, UnicodeScript.CYRILLIC, UnicodeScript.GREEK);

    /** Cyrillic and Greek letters that look (almost) identical to English letters. */
    private static final String LOOKALIKES =
            "\u0430\u0441\u0435\u04BB\u0456\u0458\u04CF\u043E\u0440\u051B\u0455\u051D\u0445\u0443\u0501"   // Cyrillic a c e h i j l o p q s w x y d
                    + "\u03BF\u03B1\u03BD\u03C1\u03B9\u03BA\u03C4\u03C5";                                       // Greek o a v p i k t u

    @Override
    public List<Indicator> check(ParsedUrl url) {
        if (url.ipv6Literal() || url.host().equals(url.hostUnicode())) {
            return List.of();   // plain English letters only: nothing to say here
        }
        List<Indicator> found = new ArrayList<>();
        found.add(new Indicator(Level.WARN, "The site name uses international letters. It reads as \""
                + url.hostUnicode() + "\" but is really \"" + url.host() + "\". Some letters from other "
                + "alphabets look exactly like English ones."));

        for (String label : url.hostUnicode().split("\\.")) {
            Set<UnicodeScript> scripts = scriptsOf(label);
            scripts.retainAll(CONFUSABLE_SCRIPTS);
            if (scripts.size() > 1) {
                found.add(new Indicator(Level.DANGER, "The part \"" + label
                        + "\" mixes letters from different alphabets (for example English and Cyrillic). "
                        + "This is a common way to imitate a real site."));
                break;
            }
            if (isAllLookalikes(label)) {
                found.add(new Indicator(Level.DANGER, "The part \"" + label
                        + "\" is written only with foreign letters that imitate English ones."));
                break;
            }
        }
        return found;
    }

    /** Which alphabets appear in the text (digits and hyphens belong to none, so they're ignored). */
    private static Set<UnicodeScript> scriptsOf(String text) {
        Set<UnicodeScript> scripts = EnumSet.noneOf(UnicodeScript.class);
        text.codePoints().forEach(cp -> {
            UnicodeScript script = UnicodeScript.of(cp);
            if (script != UnicodeScript.COMMON && script != UnicodeScript.INHERITED) {
                scripts.add(script);
            }
        });
        return scripts;
    }

    /** true if every letter is a Cyrillic/Greek look-alike of an English letter. */
    private static boolean isAllLookalikes(String label) {
        boolean sawLetter = false;
        for (int cp : label.codePoints().toArray()) {
            if (!Character.isLetter(cp)) {
                continue;
            }
            sawLetter = true;
            if (LOOKALIKES.indexOf(cp) < 0) {
                return false;
            }
        }
        return sawLetter;
    }
}