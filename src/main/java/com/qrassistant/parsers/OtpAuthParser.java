package com.qrassistant.content.parsers;

import com.qrassistant.content.ContentParser;
import com.qrassistant.content.QrContent;
import com.qrassistant.content.TextContent;

import java.util.Locale;
import java.util.Optional;

/**
 * Flags two-factor-authentication (2FA) setup codes as SENSITIVE text. (X4)
 *
 *   otpauth://totp/Google:me@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Google
 *   otpauth-migration://offline?data=...    (an Authenticator EXPORT - contains ALL your 2FA secrets)
 *
 * Anyone with the secret can generate your login codes, so the preview hides it and warns.
 */
public class OtpAuthParser implements ContentParser {

    @Override
    public Optional<QrContent> parse(String text) {
        String lower = text.strip().toLowerCase(Locale.ROOT);
        if (lower.startsWith("otpauth://") || lower.startsWith("otpauth-migration://")) {
            return Optional.of(new TextContent(text, true));
        }
        return Optional.empty();
    }
}