package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.ResolveIdentityVerificationUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationDecision;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.IdentityVerificationUpdatedEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolveIdentityVerificationUseCaseTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ResolveIdentityVerificationUseCase resolveIdentityVerificationUseCase;

    private Profile profileWith(VerificationStatus status) {
        return Profile.builder().userId("user-1").identityVerificationStatus(status).build();
    }

    @Test
    void execute_deberiaVerificarElPerfilYPublicarElEvento_cuandoSeAprueba() {
        Profile profile = profileWith(VerificationStatus.PENDING_REVIEW);
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        Profile result = resolveIdentityVerificationUseCase.execute("user-1", VerificationDecision.APPROVED, null,
                "hitl-1");

        assertThat(result.getIdentityVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(result.getVerificationReason()).isNull();
        assertThat(result.getVerificationReviewedAt()).isNotNull();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq(EventType.IDENTITY_VERIFICATION_UPDATED), eq("user-1"), payload.capture());
        IdentityVerificationUpdatedEvent event = (IdentityVerificationUpdatedEvent) payload.getValue();
        assertThat(event.status()).isEqualTo("VERIFIED");
        assertThat(event.reviewedBy()).isEqualTo("hitl-1");
        assertThat(event.reason()).isNull();
    }

    @Test
    void execute_deberiaRechazarConMotivoRecortado_cuandoSeRechaza() {
        Profile profile = profileWith(VerificationStatus.PENDING_REVIEW);
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        Profile result = resolveIdentityVerificationUseCase.execute("user-1", VerificationDecision.REJECTED,
                "  Documento ilegible ", "hitl-1");

        assertThat(result.getIdentityVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(result.getVerificationReason()).isEqualTo("Documento ilegible");
        verify(eventPublisher).publish(eq(EventType.IDENTITY_VERIFICATION_UPDATED), eq("user-1"),
                any(IdentityVerificationUpdatedEvent.class));
    }

    @Test
    void execute_deberiaExigirMotivo_cuandoSeRechazaSinExplicacion() {
        Profile profile = profileWith(VerificationStatus.PENDING_REVIEW);
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> resolveIdentityVerificationUseCase.execute("user-1",
                VerificationDecision.REJECTED, null, "hitl-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> resolveIdentityVerificationUseCase.execute("user-1",
                VerificationDecision.REJECTED, "   ", "hitl-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        verify(profileRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void execute_deberiaSerIdempotente_cuandoLaDecisionYaEstabaAplicada() {
        Profile verificado = profileWith(VerificationStatus.VERIFIED);
        Profile rechazado = profileWith(VerificationStatus.REJECTED);
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(verificado))
                .thenReturn(Optional.of(rechazado));

        Profile first = resolveIdentityVerificationUseCase.execute("user-1", VerificationDecision.APPROVED, null,
                "hitl-1");
        Profile second = resolveIdentityVerificationUseCase.execute("user-1", VerificationDecision.REJECTED,
                "motivo", "hitl-1");

        assertThat(first).isSameAs(verificado);
        assertThat(second).isSameAs(rechazado);
        verify(profileRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoNoHayDocumentoPendienteDeRevision() {
        Profile profile = profileWith(VerificationStatus.UNVERIFIED);
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> resolveIdentityVerificationUseCase.execute("user-1",
                VerificationDecision.APPROVED, null, "hitl-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pendiente de revisión");
        verify(profileRepository, never()).save(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElPerfilNoExiste() {
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolveIdentityVerificationUseCase.execute("user-1",
                VerificationDecision.APPROVED, null, "hitl-1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Perfil no encontrado");
    }
}
