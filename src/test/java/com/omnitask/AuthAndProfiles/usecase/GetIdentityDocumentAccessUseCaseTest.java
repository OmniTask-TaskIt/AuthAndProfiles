package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.AzureBlobService;
import com.omnitask.AuthAndProfiles.application.services.SignedUrl;
import com.omnitask.AuthAndProfiles.application.usecases.GetIdentityDocumentAccessUseCase;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.DocumentAccessResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetIdentityDocumentAccessUseCaseTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private AzureBlobService azureBlobService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private GetIdentityDocumentAccessUseCase getIdentityDocumentAccessUseCase;

    @Test
    void execute_deberiaEntregarElEnlaceTemporalYAuditarElAcceso() {
        LocalDateTime submittedAt = LocalDateTime.now();
        Profile profile = Profile.builder().userId("user-1").documentBlobName("user-1/abc.pdf")
                .documentType("CEDULA").documentContentType("application/pdf").documentSubmittedAt(submittedAt)
                .build();
        Instant expiresAt = Instant.now().plusSeconds(600);
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));
        when(azureBlobService.generateIdentityDocumentReadUrl("user-1/abc.pdf"))
                .thenReturn(new SignedUrl("https://blob/doc?sig=x", expiresAt));

        DocumentAccessResponseDTO result = getIdentityDocumentAccessUseCase.execute("user-1", "admin@omnitask.com");

        assertThat(result.url()).isEqualTo("https://blob/doc?sig=x");
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        assertThat(result.documentType()).isEqualTo("CEDULA");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.submittedAt()).isEqualTo(submittedAt);
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq("user-1"),
                argThat((SecurityAuditEvent e) -> e.action().equals("IDENTITY_DOCUMENT_ACCESSED")
                        && e.actor().equals("admin@omnitask.com") && e.subject().equals("user-1")));
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElPerfilNoTieneDocumento() {
        Profile sinBlob = Profile.builder().userId("user-1").build();
        Profile blobEnBlanco = Profile.builder().userId("user-2").documentBlobName("  ").build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(sinBlob));
        when(profileRepository.findByUserId("user-2")).thenReturn(Optional.of(blobEnBlanco));

        assertThatThrownBy(() -> getIdentityDocumentAccessUseCase.execute("user-1", "admin@omnitask.com"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("no tiene un documento");
        assertThatThrownBy(() -> getIdentityDocumentAccessUseCase.execute("user-2", "admin@omnitask.com"))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(azureBlobService, eventPublisher);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElPerfilNoExiste() {
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getIdentityDocumentAccessUseCase.execute("user-1", "admin@omnitask.com"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Perfil no encontrado");
    }
}
