package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.SwitchRoleUseCase;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwitchRoleUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private SwitchRoleUseCase switchRoleUseCase;

    @Test
    void execute_deberiaCambiarElRolYRetornarNuevosTokens() {
        // Arrange
        User user = User.builder().email("test@gmail.com").role(Role.SEEKER).build();
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken("test@gmail.com", "PROVIDER")).thenReturn("access-token");
        when(jwtService.generateRefreshToken("test@gmail.com")).thenReturn("refresh-token");

        // Act
        AuthResponseDTO response = switchRoleUseCase.execute("test@gmail.com", Role.PROVIDER);

        // Assert
        assertThat(user.getRole()).isEqualTo(Role.PROVIDER);
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository).save(user);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoSeIntentaAsignarRolAdmin() {
        // Arrange
        User user = User.builder().email("test@gmail.com").role(Role.SEEKER).build();
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> switchRoleUseCase.execute("test@gmail.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("administrador");
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioNoExiste() {
        // Arrange
        when(userRepository.findByEmail("noexiste@gmail.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> switchRoleUseCase.execute("noexiste@gmail.com", Role.PROVIDER))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }
}
