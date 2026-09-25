package com.omnitask.AuthAndProfiles.domain.exceptions;

/** Conflicto con el estado actual del recurso (HTTP 409): por ejemplo, una reseña o un reporte duplicados. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
