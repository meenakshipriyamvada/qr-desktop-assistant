package com.qrassistant.content.parsers;

import com.qrassistant.content.ContactContent;
import com.qrassistant.content.ContentParser;
import com.qrassistant.content.QrContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Recognizes vCard contacts (BEGIN:VCARD ... END:VCARD).
 *
 * Reads: FN (full name) or N (Last;First;...), ORG, TEL, EMAIL, URL.
 * Known limitation: old vCard 2.1 "QUOTED-PRINTABLE" values are shown as they are (rare in QR codes).
 */
public class VCardParser implements ContentParser {

    @Override
    public Optional<QrContent> parse(String text) {
        if (!text.strip().toUpperCase(Locale.ROOT).startsWith("BEGIN:VCARD")) {
            return Optional.empty();
        }

        String fullName = "";
        String structuredName = "";
        String organization = "";
        List<String> phones = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        List<String> websites = new ArrayList<>();

        for (com.qrassistant.content.parsers.ContentLines.Property p : com.qrassistant.content.parsers.ContentLines.parse(text)) {
            String value = p.value().strip();
            if (value.isEmpty()) {
                continue;
            }
            switch (p.name()) {
                case "FN" -> fullName = fullName.isEmpty() ? value : fullName;
                case "N" -> structuredName = structuredName.isEmpty() ? nameFromParts(value) : structuredName;
                case "ORG" -> organization = organization.isEmpty() ? value.split(";", 2)[0].strip() : organization;
                case "TEL" -> phones.add(value);
                case "EMAIL" -> emails.add(value);
                case "URL" -> websites.add(value);
                default -> { /* other fields (address, birthday, photo...) are ignored */ }
            }
        }
        String name = fullName.isEmpty() ? structuredName : fullName;
        return Optional.of(new ContactContent(text, name, organization, phones, emails, websites));
    }

    /** "Doe;John;Middle;Dr.;Jr." -> "John Doe" (N = family name ; given name ; ...). */
    private static String nameFromParts(String value) {
        String[] parts = value.split(";", -1);
        String family = parts[0].strip();
        String given = parts.length > 1 ? parts[1].strip() : "";
        return (given + " " + family).strip();
    }
}