package com.qrassistant.content;

/**
 * A web address (http or https).
 *
 * @param url the address with surrounding spaces removed. Not checked yet:
 *            step 9 parses it and looks for warning signs.
 */
public record UrlContent(String rawText, String url) implements QrContent {
}