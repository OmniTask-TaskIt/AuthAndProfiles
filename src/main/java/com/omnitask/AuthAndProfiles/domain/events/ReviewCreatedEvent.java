package com.omnitask.AuthAndProfiles.domain.events;

import java.time.Instant;

public record ReviewCreatedEvent(
        String reviewId,
        String taskId,
        String reviewerId,
        String revieweeId,
        int rating,
        Instant createdAt) {
}
