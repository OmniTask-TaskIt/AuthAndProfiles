package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.AuthService;
import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.application.services.IpRateLimiterService;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.*;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private ResendEmailService resendEmailService;
    @Mock
    private IpRateLimiterService ipRateLimiterService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequestDTO registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequestDTO();
        registerRequest.setEmail("test@gmail.com");
        registerRequest.setPassword("Password1!");
        registerRequest.setName("Test User");
        registerRequest.setRole(Role.SEEKER);
        registerRequest.setAcceptedTerms(true);
    }

    @Test
    void registerUser_deberiaCrearUsuarioYEnviarOtp_cuandoElEmailEsNuevo() {
        // Arrange
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = authService.registerUser(registerRequest);

        // Assert
        assertThat(result.getEmail()).isEqualTo(registerRequest.getEmail());
        assertThat(result.getPasswordHash()).isEqualTo("hashedPassword");
        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        verify(resendEmailService).sendOtpEmail(eq(registerRequest.getEmail()), anyString());
        verify(tokenRedisRepository).saveRefreshToken(eq("otp:" + registerRequest.getEmail()), anyString(), eq(600000L));
    }

    @Test
    void registerUser_deberiaLanzarExcepcion_cuandoElEmailYaExiste() {
        // Arrange
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.of(new User()));

        // Act & Assert
        assertThatThrownBy(() -> authService.registerUser(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya está registrado");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_deberiaLanzarExcepcion_cuandoNoAceptaTerminos() {
        // Arrange
        registerRequest.setAcceptedTerms(false);
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.registerUser(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("términos");
    }

    @Test
    void verifyEmail_deberiaActivarCuenta_cuandoElOtpEsValido() {
        // Arrange
        VerifyOtpRequestDTO request = new VerifyOtpRequestDTO();
        request.setEmail("test@gmail.com");
        request.setOtpCode("123456");
        User user = User.builder().email("test@gmail.com").build();

        when(tokenRedisRepository.getRefreshToken("otp:test@gmail.com")).thenReturn("123456");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        // Act
        authService.verifyEmail(request);

        // Assert
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmail_deberiaLanzarExcepcion_cuandoElOtpNoCoincide() {
        // Arrange
        VerifyOtpRequestDTO request = new VerifyOtpRequestDTO();
        request.setEmail("test@gmail.com");
        request.setOtpCode("000000");
        when(tokenRedisRepository.getRefreshToken("otp:test@gmail.com")).thenReturn("123456");

        // Act & Assert
        assertThatThrownBy(() -> authService.verifyEmail(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Código OTP inválido");
    }

    @Test
    void login_deberiaRetornarTokens_cuandoLasCredencialesSonValidas() {
        // Arrange
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("test@gmail.com");
        request.setPassword("Password1!");
        User user = User.builder().email("test@gmail.com").passwordHash("hashed").role(Role.SEEKER)
                .emailVerified(true).build();

        when(ipRateLimiterService.isBlocked("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1!", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken("test@gmail.com", "SEEKER")).thenReturn("access-token");
        when(jwtService.generateRefreshToken("test@gmail.com")).thenReturn("refresh-token");

        // Act
        AuthResponseDTO response = authService.login(request, "127.0.0.1");

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        verify(ipRateLimiterService).resetAttempts("127.0.0.1");
    }

    @Test
    void login_deberiaLanzarExcepcion_cuandoLaIpEstaBloqueada() {
        // Arrange
        when(ipRateLimiterService.isBlocked("127.0.0.1")).thenReturn(true);
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("test@gmail.com");
        request.setPassword("Password1!");

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("bloqueada");
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void login_deberiaLanzarExcepcion_cuandoLaContraseñaNoCoincide() {
        // Arrange
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("test@gmail.com");
        request.setPassword("wrong");
        User user = User.builder().email("test@gmail.com").passwordHash("hashed").build();

        when(ipRateLimiterService.isBlocked("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Credenciales inválidas");
        verify(ipRateLimiterService).recordFailedAttempt("127.0.0.1");
    }

    @Test
    void refreshToken_deberiaRetornarNuevoAccessToken_cuandoElTokenEsValido() {
        // Arrange
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO();
        request.setEmail("test@gmail.com");
        request.setRefreshToken("refresh-token");
        User user = User.builder().email("test@gmail.com").role(Role.SEEKER).build();

        when(tokenRedisRepository.getRefreshToken("test@gmail.com")).thenReturn("refresh-token");
        when(jwtService.isTokenValid("refresh-token", "test@gmail.com")).thenReturn(true);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken("test@gmail.com", "SEEKER")).thenReturn("new-access-token");

        // Act
        AuthResponseDTO response = authService.refreshToken(request);

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
    }

    @Test
    void refreshToken_deberiaLanzarExcepcion_cuandoElTokenAlmacenadoNoCoincide() {
        // Arrange
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO();
        request.setEmail("test@gmail.com");
        request.setRefreshToken("refresh-token");
        when(tokenRedisRepository.getRefreshToken("test@gmail.com")).thenReturn("different-token");

        // Act & Assert
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("inválido o expirado");
    }
}