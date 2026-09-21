package com.omnitask.AuthAndProfiles.domain.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Entrante desde Security and Audit: action es SUSPEND, BLOCK o REINSTATE. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountSanctionAppliedEvent(
        String userId,
        String action,
        String reason,
        String reportId) {
}
