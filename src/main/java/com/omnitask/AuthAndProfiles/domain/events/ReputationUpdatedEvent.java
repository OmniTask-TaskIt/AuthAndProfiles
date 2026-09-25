package com.omnitask.AuthAndProfiles.domain.events;

import java.time.Instant;

/** Se publica cada vez que cambia el promedio de reputación de un usuario, para que otros MS lo reflejen. */
public record ReputationUpdatedEvent(
        String userId,
        float reputationScore,
        int totalReviews,
        Instant updatedAt) {
}
