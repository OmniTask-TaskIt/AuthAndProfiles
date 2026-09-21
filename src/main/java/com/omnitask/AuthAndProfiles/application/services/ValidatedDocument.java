package com.omnitask.AuthAndProfiles.application.services;

/** Resultado de validar un documento: extensión y content type detectados por su firma (magic bytes). */
public record ValidatedDocument(String extension, String contentType) {
}
