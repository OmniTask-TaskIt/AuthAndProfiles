package com.omnitask.AuthAndProfiles.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** Sobre común de todos los mensajes (publicados y consumidos). El detalle va en payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(
        String eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String producer,
        JsonNode payload) {
}
