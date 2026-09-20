package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.GetProfileUseCase;

import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProfileUseCaseTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private GetProfileUseCase getProfileUseCase;

    @Test
    void execute_deberiaRetornarElPerfil_cuandoExiste() {
        // Arrange
        Profile profile = Profile.builder().userId("user-1").fullName("Robin").build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));

        // Act
        Profile result = getProfileUseCase.execute("user-1");

        // Assert
        assertThat(result.getFullName()).isEqualTo("Robin");
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElPerfilNoExiste() {
        // Arrange
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> getProfileUseCase.execute("user-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Perfil no encontrado");
    }
}
