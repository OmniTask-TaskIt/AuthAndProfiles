package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.UpdateProfileUseCase;

import com.omnitask.AuthAndProfiles.application.services.AzureBlobService;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateProfileUseCaseTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private AzureBlobService azureBlobService;

    @InjectMocks
    private UpdateProfileUseCase updateProfileUseCase;

    @Test
    void updateProfile_deberiaActualizarLosCamposProvistos_deUnPerfilExistente() {
        // Arrange
        Profile existente = Profile.builder().userId("user-1").description("vieja").build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(existente));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Profile result = updateProfileUseCase.updateProfile("user-1", "nueva descripción", null, "Bogotá",
                List.of("plomería"));

        // Assert
        assertThat(result.getDescription()).isEqualTo("nueva descripción");
        assertThat(result.getLocationCoverage()).isEqualTo("Bogotá");
        assertThat(result.getCategories()).containsExactly("plomería");
    }

    @Test
    void updateProfile_deberiaCrearUnPerfilNuevo_cuandoNoExisteUno() {
        // Arrange
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Profile result = updateProfileUseCase.updateProfile("user-1", "descripción", null, null, null);

        // Assert
        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getDescription()).isEqualTo("descripción");
    }

    @Test
    void uploadDocument_deberiaSubirElArchivoYMarcarPendienteDeRevision() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "cedula.png", "image/png", new byte[] { 1, 2, 3 });
        Profile profile = Profile.builder().userId("user-1").build();
        when(azureBlobService.uploadFile(file)).thenReturn("https://blob/cedula.png");
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Profile result = updateProfileUseCase.uploadDocument("user-1", file);

        // Assert
        assertThat(result.getDocumentUrl()).isEqualTo("https://blob/cedula.png");
        assertThat(result.getIdentityVerificationStatus()).isEqualTo(VerificationStatus.PENDING_REVIEW);
    }

    @Test
    void uploadDocument_deberiaLanzarExcepcion_cuandoElArchivoEstaVacio() {
        // Arrange
        MultipartFile fileVacio = new MockMultipartFile("file", "vacio.png", "image/png", new byte[0]);

        // Act & Assert
        assertThatThrownBy(() -> updateProfileUseCase.uploadDocument("user-1", fileVacio))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacío");
        verify(azureBlobService, never()).uploadFile(any());
    }
}
