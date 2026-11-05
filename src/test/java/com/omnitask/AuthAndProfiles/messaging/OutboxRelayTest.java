package com.omnitask.AuthAndProfiles.messaging;

import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.rabbitmq.OutboxEvent;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.rabbitmq.OutboxEventRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.rabbitmq.OutboxRelay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final String EXCHANGE = "taskit.events";

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxRelay, "exchange", EXCHANGE);
    }

    private OutboxEvent event(String id, String routingKey) {
        return OutboxEvent.builder().id(id).topic(routingKey).key("user-1").payload("{\"eventId\":\"" + id + "\"}")
                .createdAt(Instant.now()).attempts(0).build();
    }

    /** Hace que el broker confirme (ack) el envío de esta routing key. */
    private void brokerConfirma(String routingKey) {
        doAnswer(call -> {
            ((CorrelationData) call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq(EXCHANGE), eq(routingKey), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void relay_deberiaEnviarYBorrarCadaEventoPendiente() {
        OutboxEvent a = event("a", "taskit.auth.events");
        OutboxEvent b = event("b", "taskit.auth.audit");
        when(outboxEventRepository.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of(a, b));
        brokerConfirma("taskit.auth.events");
        brokerConfirma("taskit.auth.audit");

        outboxRelay.relay();

        verify(outboxEventRepository).delete(a);
        verify(outboxEventRepository).delete(b);
    }

    @Test
    void relay_deberiaEnviarUnMensajeJsonPersistenteConSuIdentificador() {
        OutboxEvent a = event("a", "taskit.auth.events");
        when(outboxEventRepository.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of(a));
        brokerConfirma("taskit.auth.events");

        outboxRelay.relay();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).send(eq(EXCHANGE), eq("taskit.auth.events"), captor.capture(), correlation.capture());
        Message message = captor.getValue();
        MessageProperties props = message.getMessageProperties();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(a.getPayload());
        assertThat(props.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(props.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(props.getMessageId()).isEqualTo("a");
        assertThat(props.getHeaders()).containsEntry("key", "user-1");
        assertThat(correlation.getValue().getId()).isEqualTo("a");
    }

    @Test
    void relay_noDeberiaHacerNada_cuandoNoHayPendientes() {
        when(outboxEventRepository.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of());

        outboxRelay.relay();

        verifyNoInteractions(rabbitTemplate);
        verify(outboxEventRepository, never()).delete(any());
    }

    @Test
    void relay_deberiaBorrarElEvento_aunqueNoHayaColaLigada_yAvisarEnElLog() {
        OutboxEvent a = event("a", "taskit.auth.events");
        when(outboxEventRepository.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of(a));
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(call.getArgument(2), 312, "NO_ROUTE", EXCHANGE,
                    "taskit.auth.events"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq(EXCHANGE), eq("taskit.auth.events"), any(Message.class),
                any(CorrelationData.class));

        outboxRelay.relay();

        verify(outboxEventRepository).delete(a);
    }

    @Test
    void relay_deberiaConservarElEventoYSaltarElRestoDeLaRoutingKey_cuandoElBrokerLoRechaza() {
        OutboxEvent fallido = event("a", "taskit.auth.events");
        OutboxEvent mismaClave = event("b", "taskit.auth.events");
        OutboxEvent otraClave = event("c", "taskit.auth.audit");
        when(outboxEventRepository.findTop50ByOrderByCreatedAtAsc())
                .thenReturn(List.of(fallido, mismaClave, otraClave));
        doAnswer(call -> {
            ((CorrelationData) call.getArgument(3)).getFuture()
                    .complete(new CorrelationData.Confirm(false, "broker caído"));
            return null;
        }).when(rabbitTemplate).send(eq(EXCHANGE), eq("taskit.auth.events"), any(Message.class),
                any(CorrelationData.class));
        brokerConfirma("taskit.auth.audit");

        outboxRelay.relay();

        // El fallido se conserva con el intento y el error registrados
        assertThat(fallido.getAttempts()).isEqualTo(1);
        assertThat(fallido.getLastError()).contains("broker caído");
        verify(outboxEventRepository).save(fallido);
        verify(outboxEventRepository, never()).delete(fallido);
        // El siguiente con la misma routing key ni se intenta (se conserva el orden)
        verify(outboxEventRepository, never()).delete(mismaClave);
        verify(rabbitTemplate, never()).send(eq(EXCHANGE), eq("taskit.auth.events"),
                org.mockito.ArgumentMatchers.argThat((Message m) -> "b".equals(m.getMessageProperties().getMessageId())),
                any(CorrelationData.class));
        // Otra routing key sigue funcionando
        verify(outboxEventRepository).delete(otraClave);
    }

    @Test
    void relay_deberiaConservarElEvento_cuandoElEnvioLanzaUnaExcepcion() {
        OutboxEvent a = event("a", "taskit.auth.events");
        when(outboxEventRepository.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of(a));
        doAnswer(call -> {
            throw new IllegalStateException("conexión rechazada");
        }).when(rabbitTemplate).send(eq(EXCHANGE), eq("taskit.auth.events"), any(Message.class),
                any(CorrelationData.class));

        outboxRelay.relay();

        assertThat(a.getAttempts()).isEqualTo(1);
        assertThat(a.getLastError()).contains("conexión rechazada");
        verify(outboxEventRepository).save(a);
        verify(outboxEventRepository, never()).delete(a);
    }

    @Test
    void relay_deberiaDetenerseYRestablecerLaInterrupcion_cuandoElHiloEsInterrumpido() {
        OutboxEvent a = event("a", "taskit.auth.events");
        OutboxEvent b = event("b", "taskit.auth.audit");
        when(outboxEventRepository.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of(a, b));
        // El broker nunca confirma y el hilo está interrumpido: la espera lanza InterruptedException.
        Thread.currentThread().interrupt();

        try {
            outboxRelay.relay();

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(outboxEventRepository, never()).delete(any());
            verify(outboxEventRepository, never()).save(any());
            verify(rabbitTemplate, never()).send(eq(EXCHANGE), eq("taskit.auth.audit"), any(Message.class),
                    any(CorrelationData.class));
        } finally {
            Thread.interrupted(); // limpia la bandera para no afectar a otros tests
        }
    }
}
