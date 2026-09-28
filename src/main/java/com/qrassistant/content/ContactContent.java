package com.qrassistant.content;

import java.util.List;

/**
 * A contact card (vCard or MeCard).
 * Any field may be empty; the lists may be empty.
 */
public record ContactContent(String rawText, String name, String organization,
                             List<String> phones, List<String> emails, List<String> websites)
        implements QrContent {

    public ContactContent {
        // Unmodifiable copies, so nobody can change the lists after the fact.
        phones = List.copyOf(phones);
        emails = List.copyOf(emails);
        websites = List.copyOf(websites);
    }
}