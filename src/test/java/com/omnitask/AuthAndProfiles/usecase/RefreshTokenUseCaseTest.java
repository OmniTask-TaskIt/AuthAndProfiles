package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.RefreshTokenUseCase;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.exceptions.AccountRestrictedException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.RefreshTokenRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
class RefreshTokenUseCaseTest {

    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenUseCase refreshTokenUseCase;

    private RefreshTokenRequestDTO request;

    @BeforeEach
    void setUp() {
        request = new RefreshTokenRequestDTO();
        request.setEmail("test@gmail.com");
        request.setRefreshToken("refresh-token");
    }

    @Test
    void execute_deberiaRetornarNuevoAccessToken_cuandoElRefreshTokenEsValido() {
        // Arrange
        User user = User.builder().email("test@gmail.com").role(Role.SEEKER).build();
        when(tokenRedisRepository.getRefreshToken("test@gmail.com")).thenReturn("refresh-token");
        when(jwtService.isTokenValid("refresh-token", "test@gmail.com")).thenReturn(true);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken("test@gmail.com", "SEEKER")).thenReturn("new-access-token");

        // Act
        AuthResponseDTO response = refreshTokenUseCase.execute(request);

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElTokenAlmacenadoNoCoincide() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("test@gmail.com")).thenReturn("otro-token");

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenUseCase.execute(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("inválido o expirado");
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElJwtNoEsValido() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("test@gmail.com")).thenReturn("refresh-token");
        when(jwtService.isTokenValid("refresh-token", "test@gmail.com")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenUseCase.execute(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no autorizado");
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoNoHayRefreshTokenAlmacenado() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("test@gmail.com")).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenUseCase.execute(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("inválido o expirado");
        verify(jwtService, never()).isTokenValid(any(), any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioNoExiste() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("test@gmail.com")).thenReturn("refresh-token");
        when(jwtService.isTokenValid("refresh-token", "test@gmail.com")).thenReturn(true);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenUseCase.execute(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoLaCuentaEstaBloqueada() {
        // Arrange
        User user = User.builder().email("test@gmail.com").role(Role.SEEKER)
                .accountStatus(AccountStatus.BLOCKED).build();
        when(tokenRedisRepository.getRefreshToken("test@gmail.com")).thenReturn("refresh-token");
        when(jwtService.isTokenValid("refresh-token", "test@gmail.com")).thenReturn(true);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenUseCase.execute(request))
                .isInstanceOf(AccountRestrictedException.class)
                .hasMessageContaining("bloqueada");
        verify(jwtService, never()).generateAccessToken(any(), any());
    }
}
