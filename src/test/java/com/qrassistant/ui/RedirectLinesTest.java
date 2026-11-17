package com.qrassistant.ui;

import com.qrassistant.urlcheck.RedirectResult;
import com.qrassistant.urlcheck.RedirectResult.Ending;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wording of the redirect result: honest, never "safe", and always clear about what Open does. */
class RedirectLinesTest {

    private static String text(RedirectResult result) {
        return String.join("\n", PreviewModel.redirectLines(result));
    }

    @Test
    void arrivedSaysAppearedAndShowsTheRealSite() {
        String text = text(new RedirectResult(
                List.of("https://short.example/x", "https://xn--bcher-kva.de/"), Ending.ARRIVED, ""));
        assertTrue(text.contains("appeared to redirect once"));
        assertTrue(text.contains("Real site there: xn--bcher-kva.de"));
        assertTrue(text.contains("still opens the original address"));
        assertFalse(text.toLowerCase().contains("safe"));
    }

    @Test
    void hiddenCharactersInTheTargetAreRevealed() {
        String text = text(new RedirectResult(
                List.of("https://a.example/", "https://b.example/‮gnp.exe"), Ending.ARRIVED, ""));
        assertTrue(text.contains("⟦U+202E⟧"));
        assertFalse(text.contains("‮"));
    }

    @Test
    void stoppedExplainsWhy() {
        String text = text(new RedirectResult(List.of("https://a.example/", "http://127.0.0.1/"),
                Ending.STOPPED, "it points to this computer"));
        assertTrue(text.contains("Not followed further: it points to this computer."));
    }

    @Test
    void everyEndingMentionsTheOriginalAddress() {
        for (Ending ending : Ending.values()) {
            String text = text(new RedirectResult(List.of("https://a.example/"), ending, "reason"));
            assertTrue(text.contains("still opens the original address"), ending.name());
            assertFalse(text.toLowerCase().contains("safe"), ending.name());
        }
    }
}
