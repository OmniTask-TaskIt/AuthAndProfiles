package com.omnitask.AuthAndProfiles.domain.events;

import java.time.Instant;

public record AccountDeletedEvent(
        String userId,
        String email,
        Instant deletedAt) {
}
