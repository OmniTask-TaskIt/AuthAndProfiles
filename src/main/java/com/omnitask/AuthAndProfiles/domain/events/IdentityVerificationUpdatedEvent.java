package com.omnitask.AuthAndProfiles.domain.events;

import java.time.Instant;

/** Cambió el estado de verificación de identidad (VERIFIED o REJECTED). */
public record IdentityVerificationUpdatedEvent(
        String userId,
        String status,
        String reason,
        String reviewedBy,
        Instant updatedAt) {
}
