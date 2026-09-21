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
    void updateProfile_deberiaActualizarTodosLosCampos_cuandoTodosSeProveen() {
        // Arrange
        Profile existente = Profile.builder().userId("user-1").build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(existente));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Profile result = updateProfileUseCase.updateProfile("user-1", "descripción", "https://blob/foto.png",
                "Medellín", List.of("electricidad"));

        // Assert
        assertThat(result.getDescription()).isEqualTo("descripción");
        assertThat(result.getPhotoUrl()).isEqualTo("https://blob/foto.png");
        assertThat(result.getLocationCoverage()).isEqualTo("Medellín");
        assertThat(result.getCategories()).containsExactly("electricidad");
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateProfile_noDeberiaModificarNingunCampo_cuandoTodosLosParametrosSonNulos() {
        // Arrange
        Profile existente = Profile.builder().userId("user-1").description("vieja").photoUrl("foto-vieja")
                .locationCoverage("Cali").categories(List.of("pintura")).build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(existente));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Profile result = updateProfileUseCase.updateProfile("user-1", null, null, null, null);

        // Assert
        assertThat(result.getDescription()).isEqualTo("vieja");
        assertThat(result.getPhotoUrl()).isEqualTo("foto-vieja");
        assertThat(result.getLocationCoverage()).isEqualTo("Cali");
        assertThat(result.getCategories()).containsExactly("pintura");
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(profileRepository).save(existente);
    }

}
