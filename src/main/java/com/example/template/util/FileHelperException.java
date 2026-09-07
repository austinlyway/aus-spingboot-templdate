package com.example.template.util;

/**
 * Unchecked wrapper for file I/O failures inside {@link FileHelper}.
 */
public class FileHelperException extends RuntimeException {

    public FileHelperException(String message) {
        super(message);
    }

    public FileHelperException(String message, Throwable cause) {
        super(message, cause);
    }
}