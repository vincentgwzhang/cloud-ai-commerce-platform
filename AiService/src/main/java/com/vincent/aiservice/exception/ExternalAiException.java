package com.vincent.aiservice.exception;

/** Raised when an external AI dependency (OpenAI, vector store) is misconfigured or unavailable. */
public class ExternalAiException extends RuntimeException {

    public ExternalAiException(String message) {
        super(message);
    }

    public ExternalAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
