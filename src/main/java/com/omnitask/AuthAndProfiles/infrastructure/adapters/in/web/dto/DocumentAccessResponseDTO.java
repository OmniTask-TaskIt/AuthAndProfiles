package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import java.time.Instant;
import java.time.LocalDateTime;

/** Enlace temporal de solo lectura al documento de identidad de un usuario (para quien revisa). */
public record DocumentAccessResponseDTO(
        String url,
        Instant expiresAt,
        String documentType,
        String contentType,
        LocalDateTime submittedAt) {
}
