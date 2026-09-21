package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.messaging.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Publica dejando el evento en la tabla outbox; OutboxRelay se encarga de enviarlo a Kafka. */
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher implements EventPublisher {

    static final String PRODUCER = "auth-profile-service";
    static final int SCHEMA_VERSION = 1;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(EventType type, String key, Object payload) {
        JsonNode payloadNode = objectMapper.valueToTree(payload);
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                type.getEventName(),
                SCHEMA_VERSION,
                Instant.now(),
                PRODUCER,
                payloadNode);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + type.getEventName(), e);
        }

        outboxEventRepository.save(OutboxEvent.builder()
                .id(envelope.eventId())
                .topic(type.getTopic())
                .key(key)
                .payload(json)
                .createdAt(envelope.occurredAt())
                .attempts(0)
                .build());
    }
}
