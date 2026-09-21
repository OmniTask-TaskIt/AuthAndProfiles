package com.omnitask.AuthAndProfiles.infrastructure.config;

import com.omnitask.AuthAndProfiles.domain.events.Topics;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableScheduling
public class KafkaConfig {

    /**
     * Manejo de errores de los consumidores: 3 reintentos con 1 s de espera; luego el mensaje se envía al
     * topic de dead letter (uno solo para todo el MS, por el límite de hubs de Event Hubs) y se sigue adelante.
     * Los errores de datos (evento inválido, usuario inexistente) no se reintentan porque nunca se arreglan solos.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(Topics.DEAD_LETTER, -1));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, NotFoundException.class);
        return errorHandler;
    }
}
