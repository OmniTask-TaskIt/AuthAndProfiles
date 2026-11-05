package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Evento pendiente de enviar a RabbitMQ (patrón outbox). Se guarda en Mongo al ocurrir el hecho de negocio y
 * OutboxRelay lo envía y lo borra. Así una caída del broker no pierde eventos ni frena al usuario.
 * El campo {@code topic} se llama así por compatibilidad con los documentos ya guardados; contiene la routing key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outbox_events")
public class OutboxEvent {

    /** Coincide con el eventId del sobre. */
    @Id
    private String id;
    private String topic;
    private String key;
    /** Sobre completo serializado a JSON. */
    private String payload;
    private Instant createdAt;
    private int attempts;
    private String lastError;
}
