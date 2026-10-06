package com.omnitask.AuthAndProfiles.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.omnitask.AuthAndProfiles.application.usecases.RecordTaskCompletedUseCase;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.kafka.ProcessedEvent;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.kafka.ProcessedEventRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.kafka.TaskEventsListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskEventsListenerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private RecordTaskCompletedUseCase recordTaskCompletedUseCase;

    private TaskEventsListener listener;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        listener = new TaskEventsListener(mapper, processedEventRepository, recordTaskCompletedUseCase);
    }

    private String envelope(String eventId, String eventType, String payloadJson) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\",\"version\":1,"
                + "\"occurredAt\":\"2026-10-06T10:00:00Z\",\"producer\":\"task-service\","
                + "\"payload\":" + payloadJson + "}";
    }

    @Test
    void onMessage_deberiaSumarLaTareaAlPrestadorYMarcarElEventoComoProcesado() {
        String message = envelope("evt-1", "TaskCompleted",
                "{\"taskId\":\"task-9\",\"providerId\":\"user-1\",\"campoNuevo\":\"x\"}");

        listener.onMessage(message);

        verify(recordTaskCompletedUseCase).execute("user-1");
        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-1");
        assertThat(captor.getValue().getProcessedAt()).isNotNull();
    }

    @Test
    void onMessage_deberiaIgnorarUnEventoYaProcesado() {
        when(processedEventRepository.existsById("evt-1")).thenReturn(true);

        listener.onMessage(envelope("evt-1", "TaskCompleted", "{\"providerId\":\"user-1\"}"));

        verifyNoInteractions(recordTaskCompletedUseCase);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void onMessage_deberiaIgnorarLosTiposDeEventoDesconocidos() {
        listener.onMessage(envelope("evt-2", "TaskCreated", "{\"taskId\":\"task-9\"}"));

        verifyNoInteractions(recordTaskCompletedUseCase);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void onMessage_noDeberiaMarcarElEventoComoProcesado_cuandoFallaElCasoDeUso() {
        doThrow(new IllegalStateException("Mongo caído")).when(recordTaskCompletedUseCase).execute("user-1");

        assertThatThrownBy(() -> listener.onMessage(envelope("evt-3", "TaskCompleted", "{\"providerId\":\"user-1\"}")))
                .isInstanceOf(IllegalStateException.class);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void onMessage_deberiaLanzarExcepcion_cuandoElMensajeNoEsJsonValido() {
        assertThatThrownBy(() -> listener.onMessage("esto no es json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobre de evento válido");
        assertThatThrownBy(() -> listener.onMessage(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onMessage_deberiaLanzarExcepcion_cuandoFaltaElEventIdElTipoOElPayload() {
        assertThatThrownBy(() -> listener.onMessage("{\"eventType\":\"TaskCompleted\",\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId, eventType o payload");
        assertThatThrownBy(() -> listener.onMessage("{\"eventId\":\"e\",\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.onMessage("{\"eventId\":\"e\",\"eventType\":\"TaskCompleted\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.onMessage("null"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onMessage_deberiaLanzarExcepcion_cuandoElPayloadNoTieneLaFormaEsperada() {
        // payload es un texto, no un objeto: no se puede leer como TaskCompletedEvent
        assertThatThrownBy(() -> listener.onMessage(envelope("evt-4", "TaskCompleted", "\"esto es un texto\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TaskCompleted");
        verify(processedEventRepository, never()).save(any());
    }
}
