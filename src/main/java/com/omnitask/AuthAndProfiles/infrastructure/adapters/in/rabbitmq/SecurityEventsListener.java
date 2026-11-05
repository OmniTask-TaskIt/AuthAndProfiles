package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnitask.AuthAndProfiles.application.usecases.ChangeAccountStatusUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.ResolveIdentityVerificationUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationDecision;
import com.omnitask.AuthAndProfiles.domain.events.AccountSanctionAppliedEvent;
import com.omnitask.AuthAndProfiles.domain.events.IdentityVerificationResolvedEvent;
import com.omnitask.AuthAndProfiles.domain.events.InboundEventTypes;
import com.omnitask.AuthAndProfiles.infrastructure.messaging.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Consume la cola app.rabbit.security-queue, ligada a la routing key taskit.security.events (Security and Audit HITL):
 * <ul>
 * <li>IdentityVerificationResolved: refleja la aprobación o el rechazo del documento en el perfil.</li>
 * <li>AccountSanctionApplied: suspende, bloquea o reactiva la cuenta.</li>
 * </ul>
 * Los tipos desconocidos se ignoran (compatibilidad hacia adelante). Los errores se propagan al interceptor de
 * reintentos de RabbitConfig (3 reintentos y luego dead letter).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventsListener {

    private static final String ACTOR = "security-service";

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final ResolveIdentityVerificationUseCase resolveIdentityVerificationUseCase;
    private final ChangeAccountStatusUseCase changeAccountStatusUseCase;

    @RabbitListener(queues = "${app.rabbit.security-queue}", autoStartup = "${app.rabbit.enabled:true}")
    public void onMessage(Message message) {
        process(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    public void process(String message) {
        EventEnvelope envelope = parseEnvelope(message);

        if (processedEventRepository.existsById(envelope.eventId())) {
            log.info("[RABBITMQ] Evento {} ya procesado, se ignora.", envelope.eventId());
            return;
        }

        switch (envelope.eventType()) {
            case InboundEventTypes.IDENTITY_VERIFICATION_RESOLVED -> handleVerificationResolved(envelope);
            case InboundEventTypes.ACCOUNT_SANCTION_APPLIED -> handleSanction(envelope);
            default -> {
                log.debug("[RABBITMQ] Tipo de evento ignorado: {}", envelope.eventType());
                return;
            }
        }

        processedEventRepository.save(new ProcessedEvent(envelope.eventId(), Instant.now()));
    }

    private void handleVerificationResolved(EventEnvelope envelope) {
        IdentityVerificationResolvedEvent event = readPayload(envelope, IdentityVerificationResolvedEvent.class);
        VerificationDecision decision = VerificationDecision.valueOf(String.valueOf(event.decision()));
        resolveIdentityVerificationUseCase.execute(event.userId(), decision, event.reason(), event.reviewedBy());
    }

    private void handleSanction(EventEnvelope envelope) {
        AccountSanctionAppliedEvent event = readPayload(envelope, AccountSanctionAppliedEvent.class);
        AccountStatus target = switch (String.valueOf(event.action())) {
            case "SUSPEND" -> AccountStatus.SUSPENDED;
            case "BLOCK" -> AccountStatus.BLOCKED;
            case "REINSTATE" -> AccountStatus.ACTIVE;
            default -> throw new IllegalArgumentException("Acción de sanción desconocida: " + event.action());
        };
        String actor = event.reportId() == null ? ACTOR : ACTOR + ":" + event.reportId();
        changeAccountStatusUseCase.execute(event.userId(), target, event.reason(), actor);
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

    private <T> T readPayload(EventEnvelope envelope, Class<T> type) {
        try {
            return objectMapper.treeToValue(envelope.payload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("El payload del evento " + envelope.eventType() + " es inválido.", e);
        }
    }
}
