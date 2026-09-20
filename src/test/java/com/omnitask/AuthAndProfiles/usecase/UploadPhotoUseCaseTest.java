package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.UploadPhotoUseCase;

import com.omnitask.AuthAndProfiles.application.services.AzureBlobService;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadPhotoUseCaseTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private AzureBlobService azureBlobService;

    @InjectMocks
    private UploadPhotoUseCase uploadPhotoUseCase;

    @Test
    void execute_deberiaSubirLaFotoYActualizarElPerfil() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", new byte[] { 1, 2, 3 });
        Profile profile = Profile.builder().userId("user-1").build();
        when(azureBlobService.uploadFile(file)).thenReturn("https://blob/foto.png");
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        String result = uploadPhotoUseCase.execute("user-1", file);

        // Assert
        assertThat(result).isEqualTo("https://blob/foto.png");
        assertThat(profile.getPhotoUrl()).isEqualTo("https://blob/foto.png");
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElArchivoEstaVacio() {
        // Arrange
        MultipartFile fileVacio = new MockMultipartFile("file", "vacio.png", "image/png", new byte[0]);

        // Act & Assert
        assertThatThrownBy(() -> uploadPhotoUseCase.execute("user-1", fileVacio))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacío");
        verify(azureBlobService, never()).uploadFile(any());
    }
}
