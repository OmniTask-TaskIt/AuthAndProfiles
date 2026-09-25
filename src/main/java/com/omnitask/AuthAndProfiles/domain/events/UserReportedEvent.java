package com.omnitask.AuthAndProfiles.domain.events;

import java.time.Instant;

public record UserReportedEvent(
        String reportId,
        String reporterId,
        String revieweeId,
        String reason,
        String comment,
        Instant createdAt) {
}
