package com.omnitask.AuthAndProfiles.domain.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Entrante desde HITL: decision es APPROVED o REJECTED. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdentityVerificationResolvedEvent(
        String userId,
        String decision,
        String reason,
        String reviewedBy) {
}
