package com.omnitask.AuthAndProfiles.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.Topics;
import com.omnitask.AuthAndProfiles.domain.events.UserRegisteredEvent;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.kafka.OutboxEvent;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.kafka.OutboxEventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.kafka.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private ObjectMapper realMapper() {
        return JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Test
    void publish_deberiaGuardarEnElOutboxElSobreCompletoConTopicYClave() throws Exception {
        OutboxEventPublisher publisher = new OutboxEventPublisher(outboxEventRepository, realMapper());
        UserRegisteredEvent payload = new UserRegisteredEvent("user-1", "test@gmail.com", "Robin", "SEEKER",
                "LOCAL", Instant.parse("2026-09-21T10:00:00Z"));

        publisher.publish(EventType.USER_REGISTERED, "user-1", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo(Topics.AUTH_EVENTS);
        assertThat(saved.getKey()).isEqualTo("user-1");
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();

        JsonNode envelope = realMapper().readTree(saved.getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(saved.getId());
        assertThat(envelope.get("eventType").asText()).isEqualTo("UserRegistered");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("producer").asText()).isEqualTo("auth-profile-service");
        assertThat(envelope.get("occurredAt").asText()).isNotBlank();
        assertThat(envelope.get("payload").get("userId").asText()).isEqualTo("user-1");
        assertThat(envelope.get("payload").get("email").asText()).isEqualTo("test@gmail.com");
        assertThat(envelope.get("payload").get("registeredAt").asText()).isEqualTo("2026-09-21T10:00:00Z");
    }

    @Test
    void publish_deberiaEnviarLosEventosDeAuditoriaAlTopicDeAuditoria() {
        OutboxEventPublisher publisher = new OutboxEventPublisher(outboxEventRepository, realMapper());

        publisher.publish(EventType.SECURITY_AUDIT, "test@gmail.com", java.util.Map.of("action", "LOGIN_SUCCESS"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo(Topics.AUTH_AUDIT);
    }

    @Test
    void publish_deberiaLanzarExcepcionYNoGuardar_cuandoNoSePuedeSerializarElSobre() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        doReturn(TextNode.valueOf("x")).when(mapper).valueToTree(any());
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });
        OutboxEventPublisher publisher = new OutboxEventPublisher(outboxEventRepository, mapper);

        assertThatThrownBy(() -> publisher.publish(EventType.ACCOUNT_DELETED, "user-1", new Object()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AccountDeleted");
        verify(outboxEventRepository, never()).save(any());
    }
}
