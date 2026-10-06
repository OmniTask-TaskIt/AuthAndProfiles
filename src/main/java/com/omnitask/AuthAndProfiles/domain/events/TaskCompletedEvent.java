package com.omnitask.AuthAndProfiles.domain.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Entrante desde Task Service: la tarea taskId fue completada por el prestador providerId. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskCompletedEvent(
        String taskId,
        String providerId) {
}
