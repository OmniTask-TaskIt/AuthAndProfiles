package com.omnitask.AuthAndProfiles.domain.events;

import java.time.Instant;

public record ReviewCompletedEvent(
    String reviewId,
    String taskId,
    String reviewerId,
    String revieweeId,
    int rating,
    String comment, 
    Instant createdAt
) {}