package com.qrassistant.content;

import com.qrassistant.content.parsers.UrlParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Automatic checks for content recognition.
 * Each test feeds in one piece of QR text and checks what type (and fields) come out.
 *
 * Run: right-click this file -> Run 'ContentRecognizerTest'   (or Maven: Lifecycle -> test)
 */
class ContentRecognizerTest {

    private final ContentRecognizer recognizer = new ContentRecognizer();

    // --- URLs ---

    @Test
    void httpsAddressIsUrl() {
        UrlContent url = assertInstanceOf(UrlContent.class, recognizer.recognize("https://example.com/page"));
        assertEquals("https://example.com/page", url.url());
    }

    @Test
    void capitalLettersStillUrl() {
        assertInstanceOf(UrlContent.class, recognizer.recognize("HTTPS://EXAMPLE.COM/ABC"));
    }

    @Test
    void surroundingSpacesAreRemovedButRawTextIsKept() {
        UrlContent url = assertInstanceOf(UrlContent.class, recognizer.recognize("  https://example.com  "));
        assertEquals("https://example.com", url.url());
        assertEquals("  https://example.com  ", url.rawText());   // Copy must give the original
    }

    @Test
    void addressWithoutSchemeIsText() {
        assertInstanceOf(TextContent.class, recognizer.recognize("www.example.com"));
    }

    @Test
    void javascriptSchemeIsNotUrl() {
        assertInstanceOf(TextContent.class, recognizer.recognize("javascript:alert(1)"));
    }

    @Test
    void fileSchemeIsNotUrl() {
        assertInstanceOf(TextContent.class, recognizer.recognize("file:///C:/Windows/System32"));
    }

    @Test
    void spaceInsideMeansNotUrl() {
        assertInstanceOf(TextContent.class, recognizer.recognize("https://example.com/a b"));
    }

    // --- Email ---

    @Test
    void mailtoWithSubjectAndBody() {
        EmailContent email = assertInstanceOf(EmailContent.class,
                recognizer.recognize("mailto:someone@example.com?subject=Hi%20there&body=Line%201"));
        assertEquals("someone@example.com", email.address());
        assertEquals("Hi there", email.subject());
        assertEquals("Line 1", email.body());
    }

    @Test
    void mailtoKeepsPlusSign() {
        EmailContent email = assertInstanceOf(EmailContent.class, recognizer.recognize("mailto:me+tag@gmail.com"));
        assertEquals("me+tag@gmail.com", email.address());
    }

    @Test
    void mailtoWithBrokenPercentCodeDoesNotCrash() {
        EmailContent email = assertInstanceOf(EmailContent.class,
                recognizer.recognize("mailto:a@b.com?subject=100%zz"));
        assertEquals("100%zz", email.subject());
    }

    @Test
    void matmsgFormat() {
        EmailContent email = assertInstanceOf(EmailContent.class,
                recognizer.recognize("MATMSG:TO:a@b.com;SUB:Hello;BODY:Hi\\; there;;"));
        assertEquals("a@b.com", email.address());
        assertEquals("Hello", email.subject());
        assertEquals("Hi; there", email.body());   // "\;" means a real semicolon
    }

    @Test
    void plainAddressIsEmail() {
        EmailContent email = assertInstanceOf(EmailContent.class, recognizer.recognize("someone@example.com"));
        assertEquals("someone@example.com", email.address());
    }

    @Test
    void halfAnAddressIsText() {
        assertInstanceOf(TextContent.class, recognizer.recognize("someone@"));
    }

    // --- Phone ---

    @Test
    void telNumber() {
        PhoneContent phone = assertInstanceOf(PhoneContent.class, recognizer.recognize("tel:+91 98765 43210"));
        assertEquals("+91 98765 43210", phone.number());
    }

    @Test
    void telWithNonsenseIsText() {
        assertInstanceOf(TextContent.class, recognizer.recognize("tel:call-me-maybe"));
    }

    @Test
    void bareNumberIsText() {
        assertInstanceOf(TextContent.class, recognizer.recognize("9876543210"));
    }

    // --- Unreadable ---

    @Test
    void emptyIsUnknown() {
        assertInstanceOf(UnknownContent.class, recognizer.recognize(""));
        assertInstanceOf(UnknownContent.class, recognizer.recognize("   "));
    }

    @Test
    void binaryDataIsUnknown() {
        assertInstanceOf(UnknownContent.class, recognizer.recognize("\u0001\u0002\u0003abc"));
    }

    @Test
    void wronglyDecodedTextIsUnknown() {
        assertInstanceOf(UnknownContent.class, recognizer.recognize("\uFFFD\uFFFDab\uFFFD"));
    }

    @Test
    void normalMultiLineTextIsText() {
        assertInstanceOf(TextContent.class, recognizer.recognize("Line one\nLine two\tand a tab"));
    }

    // --- Robustness ---

    @Test
    void brokenParserIsSkipped() {
        ContentParser broken = text -> {
            throw new IllegalStateException("bug in a parser");
        };
        ContentRecognizer withBrokenParser = new ContentRecognizer(List.of(broken, new UrlParser()));
        assertInstanceOf(UrlContent.class, withBrokenParser.recognize("https://example.com"));
    }
}