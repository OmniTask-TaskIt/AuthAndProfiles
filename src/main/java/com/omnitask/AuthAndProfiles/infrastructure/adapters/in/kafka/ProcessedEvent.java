package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Registro de eventos ya procesados para que un reenvío del mismo eventId no se aplique dos veces. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "processed_events")
public class ProcessedEvent {

    @Id
    private String eventId;
    private Instant processedAt;
}
