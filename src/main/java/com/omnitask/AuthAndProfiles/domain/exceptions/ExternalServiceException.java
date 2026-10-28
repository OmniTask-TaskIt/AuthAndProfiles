package com.omnitask.AuthAndProfiles.domain.exceptions;

/**
 * Falla de un servicio externo como Resend o Amazon S3 (HTTP 502).
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }
}
