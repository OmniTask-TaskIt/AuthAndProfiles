package com.omnitask.AuthAndProfiles.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.omnitask.AuthAndProfiles.application.usecases.ChangeAccountStatusUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.ResolveIdentityVerificationUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationDecision;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.kafka.ProcessedEvent;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.kafka.ProcessedEventRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.kafka.SecurityEventsListener;
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
class SecurityEventsListenerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private ResolveIdentityVerificationUseCase resolveIdentityVerificationUseCase;
    @Mock
    private ChangeAccountStatusUseCase changeAccountStatusUseCase;

    private SecurityEventsListener listener;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        listener = new SecurityEventsListener(mapper, processedEventRepository, resolveIdentityVerificationUseCase,
                changeAccountStatusUseCase);
    }

    private String envelope(String eventId, String eventType, String payloadJson) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\",\"version\":1,"
                + "\"occurredAt\":\"2026-09-21T10:00:00Z\",\"producer\":\"security-service\","
                + "\"payload\":" + payloadJson + "}";
    }

    // ------------------------------------------------------------ IdentityVerificationResolved

    @Test
    void onMessage_deberiaAprobarLaVerificacionYMarcarElEventoComoProcesado() {
        String message = envelope("evt-1", "IdentityVerificationResolved",
                "{\"userId\":\"user-1\",\"decision\":\"APPROVED\",\"reviewedBy\":\"hitl-7\",\"campoNuevo\":\"x\"}");

        listener.onMessage(message);

        verify(resolveIdentityVerificationUseCase).execute("user-1", VerificationDecision.APPROVED, null, "hitl-7");
        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-1");
        assertThat(captor.getValue().getProcessedAt()).isNotNull();
    }

    @Test
    void onMessage_deberiaRechazarLaVerificacionConSuMotivo() {
        String message = envelope("evt-2", "IdentityVerificationResolved",
                "{\"userId\":\"user-1\",\"decision\":\"REJECTED\",\"reason\":\"Ilegible\",\"reviewedBy\":\"hitl-7\"}");

        listener.onMessage(message);

        verify(resolveIdentityVerificationUseCase).execute("user-1", VerificationDecision.REJECTED, "Ilegible",
                "hitl-7");
    }

    @Test
    void onMessage_deberiaRechazarUnaDecisionDesconocida() {
        String message = envelope("evt-3", "IdentityVerificationResolved",
                "{\"userId\":\"user-1\",\"decision\":\"TAL_VEZ\"}");

        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(resolveIdentityVerificationUseCase);
        verify(processedEventRepository, never()).save(any());
    }

    // ------------------------------------------------------------ AccountSanctionApplied

    @Test
    void onMessage_deberiaSuspender_conElReporteEnElActor() {
        String message = envelope("evt-4", "AccountSanctionApplied",
                "{\"userId\":\"user-1\",\"action\":\"SUSPEND\",\"reason\":\"Fraude\",\"reportId\":\"R-1\"}");

        listener.onMessage(message);

        verify(changeAccountStatusUseCase).execute("user-1", AccountStatus.SUSPENDED, "Fraude",
                "security-service:R-1");
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void onMessage_deberiaBloquearYReactivar_sinReporteEnElActor() {
        listener.onMessage(envelope("evt-5", "AccountSanctionApplied",
                "{\"userId\":\"user-1\",\"action\":\"BLOCK\",\"reason\":\"Suplantación\"}"));
        listener.onMessage(envelope("evt-6", "AccountSanctionApplied",
                "{\"userId\":\"user-1\",\"action\":\"REINSTATE\"}"));

        verify(changeAccountStatusUseCase).execute("user-1", AccountStatus.BLOCKED, "Suplantación",
                "security-service");
        verify(changeAccountStatusUseCase).execute("user-1", AccountStatus.ACTIVE, null, "security-service");
    }

    @Test
    void onMessage_deberiaRechazarUnaAccionDeSancionDesconocida() {
        String message = envelope("evt-7", "AccountSanctionApplied",
                "{\"userId\":\"user-1\",\"action\":\"EXPLODE\"}");

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXPLODE");
        verifyNoInteractions(changeAccountStatusUseCase);
        verify(processedEventRepository, never()).save(any());
    }

    // ------------------------------------------------------------ idempotencia, tipos desconocidos y errores

    @Test
    void onMessage_deberiaIgnorarUnEventoYaProcesado() {
        when(processedEventRepository.existsById("evt-8")).thenReturn(true);

        listener.onMessage(envelope("evt-8", "AccountSanctionApplied",
                "{\"userId\":\"user-1\",\"action\":\"SUSPEND\",\"reason\":\"x\"}"));

        verifyNoInteractions(changeAccountStatusUseCase, resolveIdentityVerificationUseCase);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void onMessage_deberiaIgnorarSinMarcarLosTiposDeEventoDesconocidos() {
        listener.onMessage(envelope("evt-9", "ReportResolved", "{\"reportId\":\"R-9\"}"));

        verifyNoInteractions(changeAccountStatusUseCase, resolveIdentityVerificationUseCase);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void onMessage_noDeberiaMarcarElEventoYDebePropagar_cuandoElCasoDeUsoFalla() {
        String message = envelope("evt-10", "IdentityVerificationResolved",
                "{\"userId\":\"user-1\",\"decision\":\"APPROVED\"}");
        doThrow(new RuntimeException("mongo caído")).when(resolveIdentityVerificationUseCase)
                .execute("user-1", VerificationDecision.APPROVED, null, null);

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("mongo caído");
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void onMessage_deberiaRechazarMensajesQueNoSonUnSobreValido() {
        assertThatThrownBy(() -> listener.onMessage("esto no es json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobre de evento");
        assertThatThrownBy(() -> listener.onMessage(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.onMessage("null"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
        assertThatThrownBy(() -> listener.onMessage("{\"eventType\":\"X\",\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
        assertThatThrownBy(() -> listener.onMessage("{\"eventId\":\"e\",\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.onMessage("{\"eventId\":\"e\",\"eventType\":\"X\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(changeAccountStatusUseCase, resolveIdentityVerificationUseCase);
    }

    @Test
    void onMessage_deberiaRechazarUnPayloadConFormaIncorrecta() {
        String message = envelope("evt-11", "IdentityVerificationResolved", "\"esto es un texto\"");

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IdentityVerificationResolved");
        verifyNoInteractions(resolveIdentityVerificationUseCase);
    }
}
