package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.OtpGenerator;
import com.omnitask.AuthAndProfiles.application.services.PasswordResetAttemptService;
import com.omnitask.AuthAndProfiles.application.usecases.ForgotPasswordUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private PasswordResetAttemptService passwordResetAttemptService;
    @Mock
    private OtpGenerator otpGenerator;
    @Mock
    private ResendEmailService resendEmailService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ForgotPasswordUseCase forgotPasswordUseCase;

    private User user(AuthProvider provider, boolean emailVerified) {
        return User.builder().email("test@gmail.com").authProvider(provider).emailVerified(emailVerified).build();
    }

    @Test
    void execute_deberiaGuardarElCodigoConVidaDe15MinutosYEnviarElCorreo_cuandoLaCuentaEsLocalYVerificada() {
        // Arrange
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user(AuthProvider.LOCAL, true)));
        when(otpGenerator.generate()).thenReturn("123456");

        // Act
        forgotPasswordUseCase.execute("test@gmail.com", "127.0.0.1");

        // Assert
        verify(tokenRedisRepository).saveRefreshToken("pwd_reset:test@gmail.com", "123456", 900_000L);
        verify(passwordResetAttemptService).reset("test@gmail.com");
        verify(resendEmailService).sendPasswordResetEmail("test@gmail.com", "123456");
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq("test@gmail.com"),
                argThat((SecurityAuditEvent e) -> e.action().equals("PASSWORD_RESET_REQUESTED")
                        && "127.0.0.1".equals(e.ipAddress())));
    }

    @Test
    void execute_noDeberiaHacerNadaNiLanzarError_cuandoLaCuentaNoExiste() {
        // Arrange
        when(userRepository.findByEmail("nadie@gmail.com")).thenReturn(Optional.empty());

        // Act
        forgotPasswordUseCase.execute("nadie@gmail.com", "127.0.0.1");

        // Assert: la respuesta no puede revelar si el correo está registrado
        verifyNoInteractions(otpGenerator, tokenRedisRepository, passwordResetAttemptService, resendEmailService,
                eventPublisher);
    }

    @Test
    void execute_noDeberiaEnviarNada_cuandoLaCuentaIngresaConUnProveedorExterno() {
        // Arrange
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user(AuthProvider.GOOGLE, true)));

        // Act
        forgotPasswordUseCase.execute("test@gmail.com", "127.0.0.1");

        // Assert
        verifyNoInteractions(otpGenerator, tokenRedisRepository, resendEmailService, eventPublisher);
    }

    @Test
    void execute_noDeberiaEnviarNada_cuandoElCorreoNoEstaVerificado() {
        // Arrange
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user(AuthProvider.LOCAL, false)));

        // Act
        forgotPasswordUseCase.execute("test@gmail.com", "127.0.0.1");

        // Assert
        verifyNoInteractions(otpGenerator, tokenRedisRepository, resendEmailService, eventPublisher);
    }

    @Test
    void execute_deberiaBorrarElCodigoYPropagarElError_cuandoFallaElEnvioDelCorreo() {
        // Arrange
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user(AuthProvider.LOCAL, true)));
        when(otpGenerator.generate()).thenReturn("123456");
        doThrow(new ExternalServiceException("Error al enviar el correo de recuperación de contraseña"))
                .when(resendEmailService).sendPasswordResetEmail("test@gmail.com", "123456");

        // Act & Assert
        assertThatThrownBy(() -> forgotPasswordUseCase.execute("test@gmail.com", "127.0.0.1"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("recuperación de contraseña");
        verify(tokenRedisRepository).deleteRefreshToken("pwd_reset:test@gmail.com");
        // Solo se audita una solicitud que de verdad llegó al usuario.
        verifyNoInteractions(eventPublisher);
    }
}