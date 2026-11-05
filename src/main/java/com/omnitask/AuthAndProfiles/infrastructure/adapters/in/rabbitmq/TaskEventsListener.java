package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnitask.AuthAndProfiles.application.usecases.RecordTaskCompletedUseCase;
import com.omnitask.AuthAndProfiles.domain.events.InboundEventTypes;
import com.omnitask.AuthAndProfiles.domain.events.TaskCompletedEvent;
import com.omnitask.AuthAndProfiles.infrastructure.messaging.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Consume los eventos de Task Service (RF-AUTHPR-8):
 * <ul>
 * <li>TaskCompleted: suma una tarea completada al historial del prestador (providerId).</li>
 * </ul>
 * Consume la cola app.rabbit.task-queue, ligada a la routing key app.rabbit.task-routing-key (por defecto
 * taskit.task.events). Los tipos desconocidos se ignoran (compatibilidad hacia adelante). Los errores se propagan al
 * interceptor de reintentos de RabbitConfig (3 reintentos y luego dead letter).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskEventsListener {

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final RecordTaskCompletedUseCase recordTaskCompletedUseCase;

    @RabbitListener(queues = "${app.rabbit.task-queue}", autoStartup = "${app.rabbit.enabled:true}")
    public void onMessage(Message message) {
        process(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    public void process(String message) {
        EventEnvelope envelope = parseEnvelope(message);

        if (processedEventRepository.existsById(envelope.eventId())) {
            log.info("[RABBITMQ] Evento {} ya procesado, se ignora.", envelope.eventId());
            return;
        }

        if (!InboundEventTypes.TASK_COMPLETED.equals(envelope.eventType())) {
            log.debug("[RABBITMQ] Tipo de evento ignorado: {}", envelope.eventType());
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
