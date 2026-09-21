package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.OtpAttemptService;
import com.omnitask.AuthAndProfiles.application.services.OtpGenerator;
import com.omnitask.AuthAndProfiles.application.usecases.ResendOtpUseCase;

import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResendOtpUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private ResendEmailService resendEmailService;
    @Mock
    private OtpGenerator otpGenerator;
    @Mock
    private OtpAttemptService otpAttemptService;

    @InjectMocks
    private ResendOtpUseCase resendOtpUseCase;

    @Test
    void execute_deberiaReenviarElOtp_cuandoElCorreoNoHaSidoVerificado() {
        // Arrange
        User user = User.builder().email("test@gmail.com").emailVerified(false).build();
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(otpGenerator.generate()).thenReturn("654321");

        // Act
        resendOtpUseCase.execute("test@gmail.com");

        // Assert
        verify(resendEmailService).sendOtpEmail("test@gmail.com", "654321");
        verify(tokenRedisRepository).saveRefreshToken("otp:test@gmail.com", "654321", 600000L);
        verify(otpAttemptService).reset("test@gmail.com");
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElCorreoYaFueVerificado() {
        // Arrange
        User user = User.builder().email("test@gmail.com").emailVerified(true).build();
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> resendOtpUseCase.execute("test@gmail.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya ha sido verificado");
        verify(resendEmailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioNoExiste() {
        // Arrange
        when(userRepository.findByEmail("noexiste@gmail.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> resendOtpUseCase.execute("noexiste@gmail.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }
}
