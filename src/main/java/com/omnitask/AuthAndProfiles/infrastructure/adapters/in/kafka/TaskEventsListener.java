package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnitask.AuthAndProfiles.application.usecases.RecordTaskCompletedUseCase;
import com.omnitask.AuthAndProfiles.domain.events.InboundEventTypes;
import com.omnitask.AuthAndProfiles.domain.events.TaskCompletedEvent;
import com.omnitask.AuthAndProfiles.domain.events.Topics;
import com.omnitask.AuthAndProfiles.infrastructure.messaging.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consume los eventos de Task Service (RF-AUTHPR-8):
 * <ul>
 * <li>TaskCompleted: suma una tarea completada al historial del prestador (providerId).</li>
 * </ul>
 * El topic se puede cambiar con app.kafka.task-events-topic. Los tipos desconocidos se ignoran (compatibilidad hacia
 * adelante). Los errores se propagan al DefaultErrorHandler (reintentos y dead letter).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskEventsListener {

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final RecordTaskCompletedUseCase recordTaskCompletedUseCase;

    @KafkaListener(topics = "${app.kafka.task-events-topic:" + Topics.TASK_EVENTS + "}",
            groupId = "${app.kafka.consumer-group}", autoStartup = "${app.kafka.enabled:true}")
    public void onMessage(String message) {
        EventEnvelope envelope = parseEnvelope(message);

        if (processedEventRepository.existsById(envelope.eventId())) {
            log.info("[KAFKA] Evento {} ya procesado, se ignora.", envelope.eventId());
            return;
        }

        if (!InboundEventTypes.TASK_COMPLETED.equals(envelope.eventType())) {
            log.debug("[KAFKA] Tipo de evento ignorado: {}", envelope.eventType());
            return;
        }

        TaskCompletedEvent event = readPayload(envelope);
        recordTaskCompletedUseCase.execute(event.providerId());

        processedEventRepository.save(new ProcessedEvent(envelope.eventId(), Instant.now()));
    }

    private EventEnvelope parseEnvelope(String message) {
        EventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message, EventEnvelope.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalArgumentException("El mensaje no es un sobre de evento válido.", e);
        }
        if (envelope == null || envelope.eventId() == null || envelope.eventType() == null
                || envelope.payload() == null) {
            throw new IllegalArgumentException("El evento no tiene eventId, eventType o payload.");
        }
        return envelope;
    }

    private TaskCompletedEvent readPayload(EventEnvelope envelope) {
        try {
            return objectMapper.treeToValue(envelope.payload(), TaskCompletedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("El payload del evento " + envelope.eventType() + " es inválido.", e);
        }
    }
}
