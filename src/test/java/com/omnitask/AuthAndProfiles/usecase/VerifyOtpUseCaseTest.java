package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.OtpAttemptService;
import com.omnitask.AuthAndProfiles.application.usecases.VerifyOtpUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.exceptions.TooManyAttemptsException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.VerifyOtpRequestDTO;
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
class VerifyOtpUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private OtpAttemptService otpAttemptService;

    @InjectMocks
    private VerifyOtpUseCase verifyOtpUseCase;

    private VerifyOtpRequestDTO request;

    @BeforeEach
    void setUp() {
        request = new VerifyOtpRequestDTO();
        request.setEmail("test@gmail.com");
        request.setOtpCode("123456");
    }

    @Test
    void execute_deberiaActivarLaCuentaEInvalidarElOtp_cuandoElOtpEsValido() {
        // Arrange
        User user = User.builder().email("test@gmail.com").build();
        when(tokenRedisRepository.getRefreshToken("otp:test@gmail.com")).thenReturn("123456");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        // Act
        verifyOtpUseCase.execute(request);

        // Assert
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userRepository).save(user);
        verify(tokenRedisRepository).deleteRefreshToken("otp:test@gmail.com");
        verify(otpAttemptService).reset("test@gmail.com");
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElOtpNoCoincide() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("otp:test@gmail.com")).thenReturn("000000");
        when(otpAttemptService.registerFailureAndCheckLimit("test@gmail.com")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> verifyOtpUseCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Código OTP inválido");
        verify(userRepository, never()).save(any());
        verify(tokenRedisRepository, never()).deleteRefreshToken(anyString());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElOtpHaExpirado() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("otp:test@gmail.com")).thenReturn(null);
        when(otpAttemptService.registerFailureAndCheckLimit("test@gmail.com")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> verifyOtpUseCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Código OTP inválido");
    }

    @Test
    void execute_deberiaInvalidarElOtpYBloquear_cuandoSeAlcanzaElMaximoDeIntentos() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("otp:test@gmail.com")).thenReturn("000000");
        when(otpAttemptService.registerFailureAndCheckLimit("test@gmail.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> verifyOtpUseCase.execute(request))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessageContaining("Solicita un nuevo código");
        verify(tokenRedisRepository).deleteRefreshToken("otp:test@gmail.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioNoExiste() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("otp:test@gmail.com")).thenReturn("123456");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> verifyOtpUseCase.execute(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Usuario no encontrado");
        verify(userRepository, never()).save(any());
        verify(tokenRedisRepository, never()).deleteRefreshToken(anyString());
        verify(otpAttemptService, never()).reset(anyString());
    }
}
