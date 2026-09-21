package com.omnitask.AuthAndProfiles.domain.events;

import java.time.Instant;

/**
 * El usuario subió un documento de identidad. No contiene el archivo: quien revisa pide un enlace temporal
 * de solo lectura a GET /api/v1/admin/verification-documents/{userId}/access.
 */
public record IdentityDocumentSubmittedEvent(
        String userId,
        String email,
        String fullName,
        String documentType,
        String contentType,
        long sizeBytes,
        String blobName,
        Instant submittedAt) {
}
