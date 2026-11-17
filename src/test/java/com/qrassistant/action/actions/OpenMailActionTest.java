package com.qrassistant.action.actions;

import com.qrassistant.content.EmailContent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** We build our own mailto: link with only the fields the user saw. */
class OpenMailActionTest {

    private static Optional<URI> mailto(String address, String subject, String body) {
        return OpenMailAction.mailtoFor(new EmailContent("raw", address, subject, body));
    }

    @Test
    void addressOnly() {
        assertEquals("mailto:someone@example.com", mailto("someone@example.com", "", "").orElseThrow().toString());
    }

    @Test
    void subjectAndBodyAreEncoded() {
        assertEquals("mailto:a@b.com?subject=Hi%20there&body=Line%201%0ALine%202%20%26%20more",
                mailto("a@b.com", "Hi there", "Line 1\nLine 2 & more").orElseThrow().toString());
    }

    @Test
    void plusInAddressIsKept() {
        assertEquals("mailto:name+tag@gmail.com", mailto("name+tag@gmail.com", "", "").orElseThrow().toString());
    }

    @Test
    void addressesThatCouldSmuggleExtraFieldsAreRefused() {
        assertTrue(mailto("a@b.com?attach=C:/secret.txt", "", "").isEmpty());
        assertTrue(mailto("a@b.com&bcc=spy@evil.com", "", "").isEmpty());
        assertTrue(mailto("a@b.com,spy@evil.com", "", "").isEmpty());
        assertTrue(mailto("a@b.com%3Fattach%3Dx", "", "").isEmpty());
    }

    @Test
    void notAnAddressIsRefused() {
        assertTrue(mailto("", "Hi", "").isEmpty());
        assertTrue(mailto("not an address", "", "").isEmpty());
        assertTrue(mailto("a@b", "", "").isEmpty());
    }

    @Test
    void subjectCannotAddFields() {
        // "&bcc=" inside the subject becomes %26bcc%3D: just text, not a new field.
        assertEquals("mailto:a@b.com?subject=x%26bcc%3Dspy%40evil.com",
                mailto("a@b.com", "x&bcc=spy@evil.com", "").orElseThrow().toString());
    }
}
