package com.omnitask.AuthAndProfiles.domain.events;

import java.time.Instant;

public record AccountStatusChangedEvent(
        String userId,
        String email,
        String previousStatus,
        String newStatus,
        String reason,
        String changedBy,
        Instant changedAt) {
}
