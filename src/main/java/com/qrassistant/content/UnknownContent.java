package com.qrassistant.content;

/** Data that isn't readable text (empty, binary, or badly decoded). Only "Copy" makes sense. */
public record UnknownContent(String rawText) implements QrContent {
}