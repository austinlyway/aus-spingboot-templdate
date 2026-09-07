package com.example.template.util.json;

/**
 * Wraps any error thrown by the underlying JSON library so callers do not
 * need to know which library produced the failure.
 */
public class JsonException extends RuntimeException {

    public JsonException(String message) {
        super(message);
    }

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}