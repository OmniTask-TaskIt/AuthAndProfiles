package com.omnitask.AuthAndProfiles.domain.events;

import java.time.Instant;

public record UserRegisteredEvent(
        String userId,
        String email,
        String name,
        String role,
        String authProvider,
        Instant registeredAt) {
}
