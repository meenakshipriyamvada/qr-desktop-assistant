package com.qrassistant.content;

/**
 * What a QR code contains, after recognition.
 *
 * "sealed" = only the types listed after "permits" can exist. The compiler knows the full list,
 * so a switch over QrContent that forgets a type won't compile.
 *
 * Every type keeps rawText: the exact original text, used for "Copy".
 */
public sealed interface QrContent
        permits UrlContent, EmailContent, PhoneContent, WifiContent, ContactContent, EventContent,
        TextContent, UnknownContent {

    /** The exact text stored in the QR code. UNTRUSTED. */
    String rawText();
}