package com.qrassistant.content;

import java.util.Optional;

/**
 * Recognizes ONE format (URL, email, ...).
 *
 * RULES for every parser:
 *  - return Optional.empty() if the text is not this format
 *  - never return null, and try hard never to throw: the text is untrusted and may be anything
 */
public interface ContentParser {

    Optional<QrContent> parse(String text);
}