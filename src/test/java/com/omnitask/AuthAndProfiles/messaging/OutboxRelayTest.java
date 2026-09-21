package com.omnitask.AuthAndProfiles.messaging;

import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.kafka.OutboxEvent;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.kafka.OutboxEventRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.kafka.OutboxRelay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxRelay outboxRelay;

    private OutboxEvent event(String id, String topic) {
        return OutboxEvent.builder().id(id).topic(topic).key("user-1").payload("{\"eventId\":\"" + id + "\"}")
                .createdAt(Instant.now()).attempts(0).build();
    }

    @Test
    void relay_deberiaEnviarYBorrarCadaEventoPendiente() {
        OutboxEvent a = event("a", "taskit.auth.events");
        OutboxEvent b = event("b", "taskit.auth.audit");
        when(outboxEventRepository.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of(a, b));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        outboxRelay.relay();

        verify(kafkaTemplate).send("taskit.auth.events", "user-1", a.getPayload());
        verify(kafkaTemplate).send("taskit.auth.audit", "user-1", b.getPayload());
        verify(outboxEventRepository).delete(a);
        verify(outboxEventRepository).delete(b);
    }

    @Test
    void relay_noDeberiaHacerNada_cuandoNoHayPendientes() {
        when(outboxEventRepository.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of());

        outboxRelay.relay();

        verifyNoInteractions(kafkaTemplate);
        verify(outboxEventRepository, never()).delete(any());
    }

    @Test
    void relay_deberiaConservarElEventoYSaltarElRestoDelTopic_cuandoUnEnvioFalla() {
        OutboxEvent fallido = event("a", "taskit.auth.events");
        OutboxEvent mismoTopic = event("b", "taskit.auth.events");
        OutboxEvent otroTopic = event("c", "taskit.auth.audit");
        when(outboxEventRepository.findTop50ByOrderByCreatedAtAsc())
                .thenReturn(List.of(fallido, mismoTopic, otroTopic));
        when(kafkaTemplate.send("taskit.auth.events", "user-1", fallido.getPayload()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>failedFuture(
                        new RuntimeException("broker caído")));
        when(kafkaTemplate.send("taskit.auth.audit", "user-1", otroTopic.getPayload()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        outboxRelay.relay();

        // El fallido se conserva con el intento y el error registrados
        assertThat(fallido.getAttempts()).isEqualTo(1);
        assertThat(fallido.getLastError()).contains("broker caído");
        verify(outboxEventRepository).save(fallido);
        verify(outboxEventRepository, never()).delete(fallido);
        // El siguiente del mismo topic ni se intenta (se conserva el orden)
        verify(kafkaTemplate, never()).send("taskit.auth.events", "user-1", mismoTopic.getPayload());
        verify(outboxEventRepository, never()).delete(mismoTopic);
        // Otro topic sigue funcionando
        verify(outboxEventRepository).delete(otroTopic);
    }

    @Test
    @SuppressWarnings("unchecked")
    void relay_deberiaDetenerseYRestablecerLaInterrupcion_cuandoElHiloEsInterrumpido() throws Exception {
        OutboxEvent a = event("a", "taskit.auth.events");
        OutboxEvent b = event("b", "taskit.auth.audit");
        when(outboxEventRepository.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of(a, b));
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(anyLong(), any())).thenThrow(new InterruptedException("interrumpido"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        try {
            outboxRelay.relay();

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(outboxEventRepository, never()).delete(any());
            verify(kafkaTemplate, never()).send("taskit.auth.audit", "user-1", b.getPayload());
        } finally {
            Thread.interrupted(); // limpia la bandera para no afectar a otros tests
        }
    }
}
