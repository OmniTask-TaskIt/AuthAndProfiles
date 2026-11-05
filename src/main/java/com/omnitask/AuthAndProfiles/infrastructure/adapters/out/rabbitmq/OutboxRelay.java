package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Envía a RabbitMQ los eventos pendientes del outbox, en orden de creación. Entrega "al menos una vez":
 * los consumidores deben ser idempotentes (por eventId). Cada envío espera la confirmación (publisher confirm) del
 * broker antes de borrar el evento del outbox. Si una routing key falla, se salta lo que queda de esa routing key en
 * esta pasada para conservar su orden, y se reintenta en la siguiente.
 * <p>
 * El campo {@code topic} del outbox guarda la routing key del mensaje (taskit.auth.events, taskit.auth.audit...).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    static final long CONFIRM_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbit.exchange}")
    private String exchange;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void relay() {
        Set<String> failedRoutingKeys = new HashSet<>();

        for (OutboxEvent event : outboxEventRepository.findTop50ByOrderByCreatedAtAsc()) {
            if (failedRoutingKeys.contains(event.getTopic())) {
                continue;
            }
            try {
                send(event);
                outboxEventRepository.delete(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                failedRoutingKeys.add(event.getTopic());
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(String.valueOf(e.getMessage()));
                outboxEventRepository.save(event);
                log.warn("[RABBITMQ] No se pudo enviar el evento {} a la routing key {} (intento {}): {}",
                        event.getId(), event.getTopic(), event.getAttempts(), e.getMessage());
            }
        }
    }

    private void send(OutboxEvent event) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.getId());
        if (event.getKey() != null) {
            // RabbitMQ no tiene clave de partición; se deja como cabecera para trazabilidad (userId o correo).
            properties.setHeader("key", event.getKey());
        }
        Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);

        CorrelationData correlation = new CorrelationData(event.getId());
        rabbitTemplate.send(exchange, event.getTopic(), message, correlation);

        CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("El broker rechazó el mensaje: " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            // Ninguna cola está ligada a esa routing key todavía: RabbitMQ no guarda mensajes sin cola destino.
            log.warn("[RABBITMQ] El evento {} no llegó a ninguna cola (routing key {} sin consumidores ligados).",
                    event.getId(), event.getTopic());
        }
    }
}
