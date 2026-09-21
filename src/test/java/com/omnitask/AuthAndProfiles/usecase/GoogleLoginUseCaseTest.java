package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.GoogleLoginUseCase;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.omnitask.AuthAndProfiles.application.services.GoogleAuthService;
import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.exceptions.AccountRestrictedException;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleLoginUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private GoogleAuthService googleAuthService;
    @Mock
    private JwtService jwtService;
    @Mock
    private TokenRedisRepository tokenRedisRepository;

    @InjectMocks
    private GoogleLoginUseCase googleLoginUseCase;

    private GoogleIdToken.Payload payloadFor(String email, String name) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.set("name", name);
        return payload;
    }

    @Test
    void execute_deberiaRetornarTokens_cuandoElUsuarioDeGoogleYaExiste() {
        // Arrange
        User user = User.builder().id("user-1").email("test@gmail.com").role(Role.SEEKER).build();
        when(googleAuthService.verifyToken("google-token")).thenReturn(payloadFor("test@gmail.com", "Robin"));
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken("test@gmail.com", "SEEKER")).thenReturn("access-token");
        when(jwtService.generateRefreshToken("test@gmail.com")).thenReturn("refresh-token");

        // Act
        AuthResponseDTO response = googleLoginUseCase.execute("google-token");

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_deberiaCrearUsuarioYPerfilNuevos_cuandoEsElPrimerLoginConGoogle() {
        // Arrange
        when(googleAuthService.verifyToken("google-token")).thenReturn(payloadFor("nuevo@gmail.com", "Nuevo Usuario"));
        when(userRepository.findByEmail("nuevo@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-nuevo");
            return u;
        });
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        // Act
        AuthResponseDTO response = googleLoginUseCase.execute("google-token");

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository).save(argThat((User u) -> u.getAuthProvider() == AuthProvider.GOOGLE && u.isEmailVerified()));
        verify(profileRepository).save(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoLaCuentaDeGoogleEstaSuspendida() {
        // Arrange
        User user = User.builder().id("user-1").email("test@gmail.com").role(Role.SEEKER)
                .accountStatus(AccountStatus.SUSPENDED).build();
        when(googleAuthService.verifyToken("google-token")).thenReturn(payloadFor("test@gmail.com", "Robin"));
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> googleLoginUseCase.execute("google-token"))
                .isInstanceOf(AccountRestrictedException.class)
                .hasMessageContaining("suspendida");
        verify(jwtService, never()).generateAccessToken(any(), any());
        verify(tokenRedisRepository, never()).saveRefreshToken(any(), any(), anyLong());
    }
}
