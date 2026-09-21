package com.omnitask.AuthAndProfiles.domain.exceptions;

/**
 * Se superó un límite de intentos (HTTP 429).
 */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
