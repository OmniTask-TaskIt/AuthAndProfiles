package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.CreateProfileUseCase;

import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateProfileUseCaseTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CreateProfileUseCase createProfileUseCase;

    @Test
    void execute_deberiaRetornarElPerfilExistente_sinCrearUnoNuevo() {
        // Arrange
        Profile existente = Profile.builder().userId("user-1").build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(existente));

        // Act
        Profile result = createProfileUseCase.execute("user-1");

        // Assert
        assertThat(result).isEqualTo(existente);
        verify(profileRepository, never()).save(any());
    }

    @Test
    void execute_deberiaCrearUnPerfilNuevo_cuandoNoExisteUno() {
        // Arrange
        User user = User.builder().id("user-1").name("Robin").role(Role.SEEKER).build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Profile result = createProfileUseCase.execute("user-1");

        // Assert
        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getFullName()).isEqualTo("Robin");
        assertThat(result.getCurrentRole()).isEqualTo("SEEKER");
        assertThat(result.getReputationScore()).isEqualTo(5.0f);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioNoExiste() {
        // Arrange
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> createProfileUseCase.execute("user-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }
}
