package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Envía a Kafka los eventos pendientes del outbox, en orden de creación. Entrega "al menos una vez":
 * los consumidores deben ser idempotentes (por eventId). Si un topic falla, se salta lo que queda de ese
 * topic en esta pasada para conservar su orden, y se reintenta en la siguiente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void relay() {
        Set<String> failedTopics = new HashSet<>();

        for (OutboxEvent event : outboxEventRepository.findTop50ByOrderByCreatedAtAsc()) {
            if (failedTopics.contains(event.getTopic())) {
                continue;
            }
            try {
                kafkaTemplate.send(event.getTopic(), event.getKey(), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                outboxEventRepository.delete(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                failedTopics.add(event.getTopic());
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(String.valueOf(e.getMessage()));
                outboxEventRepository.save(event);
                log.warn("[KAFKA] No se pudo enviar el evento {} al topic {} (intento {}): {}", event.getId(),
                        event.getTopic(), event.getAttempts(), e.getMessage());
            }
        }
    }
}
