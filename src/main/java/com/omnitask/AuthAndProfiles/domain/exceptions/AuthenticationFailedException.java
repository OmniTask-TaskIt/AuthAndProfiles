package com.omnitask.AuthAndProfiles.domain.exceptions;

/**
 * Credenciales o tokens inválidos (HTTP 401).
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
