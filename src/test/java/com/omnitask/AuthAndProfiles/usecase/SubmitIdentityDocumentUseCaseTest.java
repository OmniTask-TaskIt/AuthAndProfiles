package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.AzureBlobService;
import com.omnitask.AuthAndProfiles.application.services.IdentityDocumentValidator;
import com.omnitask.AuthAndProfiles.application.services.ValidatedDocument;
import com.omnitask.AuthAndProfiles.application.usecases.SubmitIdentityDocumentUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.DocumentType;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.IdentityDocumentSubmittedEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

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
class SubmitIdentityDocumentUseCaseTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IdentityDocumentValidator identityDocumentValidator;
    @Mock
    private AzureBlobService azureBlobService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SubmitIdentityDocumentUseCase submitIdentityDocumentUseCase;

    private final ValidatedDocument pdf = new ValidatedDocument("pdf", "application/pdf");
    private MultipartFile file;
    private User user;

    @BeforeEach
    void setUp() {
        file = new MockMultipartFile("file", "cedula.pdf", "application/pdf", new byte[] { 1, 2, 3 });
        user = User.builder().id("user-1").email("test@gmail.com").build();
    }

    private void stubValidAndExistingUser() {
        when(identityDocumentValidator.validate(file)).thenReturn(pdf);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
    }

    @Test
    void execute_deberiaGuardarElDocumentoMarcarPendienteYPublicarElEvento() {
        stubValidAndExistingUser();
        Profile profile = Profile.builder().userId("user-1").fullName("Robin")
                .identityVerificationStatus(VerificationStatus.UNVERIFIED).build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));
        when(azureBlobService.uploadIdentityDocument("user-1", file, pdf)).thenReturn("user-1/abc.pdf");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        Profile result = submitIdentityDocumentUseCase.execute("user-1", DocumentType.CEDULA, file);

        assertThat(result.getIdentityVerificationStatus()).isEqualTo(VerificationStatus.PENDING_REVIEW);
        assertThat(result.getDocumentBlobName()).isEqualTo("user-1/abc.pdf");
        assertThat(result.getDocumentType()).isEqualTo("CEDULA");
        assertThat(result.getDocumentContentType()).isEqualTo("application/pdf");
        assertThat(result.getDocumentSizeBytes()).isEqualTo(3L);
        assertThat(result.getDocumentSubmittedAt()).isNotNull();
        assertThat(result.getVerificationReason()).isNull();
        verify(azureBlobService, never()).deleteIdentityDocument(any());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq(EventType.IDENTITY_DOCUMENT_SUBMITTED), eq("user-1"), payload.capture());
        IdentityDocumentSubmittedEvent event = (IdentityDocumentSubmittedEvent) payload.getValue();
        assertThat(event.userId()).isEqualTo("user-1");
        assertThat(event.email()).isEqualTo("test@gmail.com");
        assertThat(event.fullName()).isEqualTo("Robin");
        assertThat(event.documentType()).isEqualTo("CEDULA");
        assertThat(event.blobName()).isEqualTo("user-1/abc.pdf");
        assertThat(event.sizeBytes()).isEqualTo(3L);
    }

    @Test
    void execute_deberiaReemplazarElDocumentoAnterior_cuandoLaVerificacionFueRechazada() {
        stubValidAndExistingUser();
        Profile profile = Profile.builder().userId("user-1").documentBlobName("user-1/viejo.pdf")
                .verificationReason("Ilegible").identityVerificationStatus(VerificationStatus.REJECTED).build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));
        when(azureBlobService.uploadIdentityDocument("user-1", file, pdf)).thenReturn("user-1/nuevo.pdf");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        Profile result = submitIdentityDocumentUseCase.execute("user-1", DocumentType.PASSPORT, file);

        assertThat(result.getIdentityVerificationStatus()).isEqualTo(VerificationStatus.PENDING_REVIEW);
        assertThat(result.getDocumentBlobName()).isEqualTo("user-1/nuevo.pdf");
        assertThat(result.getVerificationReason()).isNull();
        verify(azureBlobService).deleteIdentityDocument("user-1/viejo.pdf");
    }

    @Test
    void execute_noDeberiaBorrarNada_cuandoElNombreDelBlobAnteriorEstaEnBlanco() {
        stubValidAndExistingUser();
        Profile profile = Profile.builder().userId("user-1").documentBlobName("  ").build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));
        when(azureBlobService.uploadIdentityDocument("user-1", file, pdf)).thenReturn("user-1/abc.pdf");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        submitIdentityDocumentUseCase.execute("user-1", DocumentType.CEDULA, file);

        verify(azureBlobService, never()).deleteIdentityDocument(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoLaIdentidadYaEstaVerificada() {
        stubValidAndExistingUser();
        Profile profile = Profile.builder().userId("user-1")
                .identityVerificationStatus(VerificationStatus.VERIFIED).build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> submitIdentityDocumentUseCase.execute("user-1", DocumentType.CEDULA, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya está verificada");
        verifyNoInteractions(azureBlobService, eventPublisher);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoYaHayUnDocumentoEnRevision() {
        stubValidAndExistingUser();
        Profile profile = Profile.builder().userId("user-1")
                .identityVerificationStatus(VerificationStatus.PENDING_REVIEW).build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> submitIdentityDocumentUseCase.execute("user-1", DocumentType.CEDULA, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("en revisión");
        verifyNoInteractions(azureBlobService, eventPublisher);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioNoExiste() {
        when(identityDocumentValidator.validate(file)).thenReturn(pdf);
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submitIdentityDocumentUseCase.execute("user-1", DocumentType.CEDULA, file))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElPerfilNoExiste() {
        stubValidAndExistingUser();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submitIdentityDocumentUseCase.execute("user-1", DocumentType.CEDULA, file))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Perfil no encontrado");
        verifyNoInteractions(azureBlobService, eventPublisher);
    }

    @Test
    void execute_deberiaFallarSinTocarNada_cuandoElArchivoEsInvalido() {
        when(identityDocumentValidator.validate(file)).thenThrow(new IllegalArgumentException("Formato no permitido"));

        assertThatThrownBy(() -> submitIdentityDocumentUseCase.execute("user-1", DocumentType.CEDULA, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato no permitido");
        verifyNoInteractions(userRepository, profileRepository, azureBlobService, eventPublisher);
    }
}
