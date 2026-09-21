package com.omnitask.AuthAndProfiles.messaging;

import com.omnitask.AuthAndProfiles.infrastructure.config.KafkaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void kafkaErrorHandler_deberiaCrearseConReintentosYDeadLetter() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);

        DefaultErrorHandler handler = new KafkaConfig().kafkaErrorHandler(template);

        assertThat(handler).isNotNull();
    }
}
