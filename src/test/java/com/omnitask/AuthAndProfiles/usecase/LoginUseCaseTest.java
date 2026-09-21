package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.exceptions.AccountRestrictedException;
import com.omnitask.AuthAndProfiles.domain.exceptions.AuthenticationFailedException;
import com.omnitask.AuthAndProfiles.application.usecases.LoginUseCase;

import com.omnitask.AuthAndProfiles.application.services.IpRateLimiterService;
import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.LoginRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private IpRateLimiterService ipRateLimiterService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private LoginUseCase loginUseCase;

    private LoginRequestDTO request;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        request = new LoginRequestDTO();
        request.setEmail("test@gmail.com");
        request.setPassword("Password1!");
    }

    @Test
    void execute_deberiaRetornarTokens_cuandoLasCredencialesSonValidas() {
        // Arrange
        User user = User.builder().email("test@gmail.com").passwordHash("hashed").role(Role.SEEKER)
                .emailVerified(true).build();
        when(ipRateLimiterService.isBlocked("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1!", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken("test@gmail.com", "SEEKER")).thenReturn("access-token");
        when(jwtService.generateRefreshToken("test@gmail.com")).thenReturn("refresh-token");

        // Act
        AuthResponseDTO response = loginUseCase.execute(request, "127.0.0.1");

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(tokenRedisRepository).saveRefreshToken("test@gmail.com", "refresh-token", 604800000L);
        verify(ipRateLimiterService).resetAttempts("127.0.0.1");
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq("test@gmail.com"),
                argThat((SecurityAuditEvent e) -> e.action().equals("LOGIN_SUCCESS") && "127.0.0.1".equals(e.ipAddress())));
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoLaIpEstaBloqueada() {
        // Arrange
        when(ipRateLimiterService.isBlocked("127.0.0.1")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> loginUseCase.execute(request, "127.0.0.1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("bloqueada");
        verify(userRepository, never()).findByEmail(any());
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), any(),
                argThat((SecurityAuditEvent e) -> e.action().equals("LOGIN_BLOCKED")));
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioNoExiste() {
        // Arrange
        when(ipRateLimiterService.isBlocked("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> loginUseCase.execute(request, "127.0.0.1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Credenciales inválidas");
        // Un usuario inexistente cuenta como UN solo intento fallido (antes se contaba dos veces).
        verify(ipRateLimiterService, times(1)).recordFailedAttempt("127.0.0.1");
        verify(passwordEncoder, never()).matches(any(), any());
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), any(),
                argThat((SecurityAuditEvent e) -> e.action().equals("LOGIN_FAILED")));
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElCorreoNoHaSidoVerificado() {
        // Arrange
        User user = User.builder().email("test@gmail.com").passwordHash("hashed").emailVerified(false).build();
        when(ipRateLimiterService.isBlocked("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1!", "hashed")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> loginUseCase.execute(request, "127.0.0.1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("verificar tu cuenta");
    }

    @Test
    void execute_deberiaRegistrarIntentoFallido_cuandoLaContrasenaEsIncorrecta() {
        // Arrange
        User user = User.builder().email("test@gmail.com").passwordHash("hashed").role(Role.SEEKER)
                .emailVerified(true).build();
        when(ipRateLimiterService.isBlocked("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1!", "hashed")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> loginUseCase.execute(request, "127.0.0.1"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("Credenciales inválidas");
        verify(ipRateLimiterService, times(1)).recordFailedAttempt("127.0.0.1");
        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoLaCuentaEstaSuspendida() {
        // Arrange
        User user = User.builder().email("test@gmail.com").passwordHash("hashed").role(Role.SEEKER)
                .emailVerified(true).accountStatus(AccountStatus.SUSPENDED).build();
        when(ipRateLimiterService.isBlocked("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1!", "hashed")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> loginUseCase.execute(request, "127.0.0.1"))
                .isInstanceOf(AccountRestrictedException.class)
                .hasMessageContaining("suspendida");
        verify(jwtService, never()).generateAccessToken(any(), any());
        verify(tokenRedisRepository, never()).saveRefreshToken(any(), any(), anyLong());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoLaCuentaEstaBloqueada() {
        // Arrange
        User user = User.builder().email("test@gmail.com").passwordHash("hashed").role(Role.SEEKER)
                .emailVerified(true).accountStatus(AccountStatus.BLOCKED).build();
        when(ipRateLimiterService.isBlocked("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1!", "hashed")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> loginUseCase.execute(request, "127.0.0.1"))
                .isInstanceOf(AccountRestrictedException.class)
                .hasMessageContaining("bloqueada");
    }
}
