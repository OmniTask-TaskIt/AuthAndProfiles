package com.omnitask.AuthAndProfiles.domain.events;

import java.time.Instant;

/** Registro de auditoría: action es LOGIN_SUCCESS, LOGIN_FAILED, LOGIN_BLOCKED o IDENTITY_DOCUMENT_ACCESSED. */
public record SecurityAuditEvent(
        String action,
        String subject,
        String actor,
        String ipAddress,
        Instant occurredAt) {
}
