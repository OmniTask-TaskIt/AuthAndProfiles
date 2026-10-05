package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.PasswordResetAttemptService;
import com.omnitask.AuthAndProfiles.application.usecases.ResetPasswordUseCase;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.exceptions.TooManyAttemptsException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ResetPasswordRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private PasswordResetAttemptService passwordResetAttemptService;
    @Mock
    private ResendEmailService resendEmailService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ResetPasswordUseCase resetPasswordUseCase;

    private ResetPasswordRequestDTO request;

    @BeforeEach
    void setUp() {
        request = new ResetPasswordRequestDTO();
        request.setEmail("test@gmail.com");
        request.setCode("123456");
        request.setNewPassword("NuevaClave1!");
    }

    @Test
    void execute_deberiaCambiarLaContrasenaCerrarSesionesYNotificar_cuandoElCodigoEsCorrecto() {
        // Arrange
        User user = User.builder().email("test@gmail.com").passwordHash("hash-viejo").build();
        when(tokenRedisRepository.getRefreshToken("pwd_reset:test@gmail.com")).thenReturn("123456");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NuevaClave1!")).thenReturn("hash-nuevo");

        // Act
        resetPasswordUseCase.execute(request, "127.0.0.1");

        // Assert
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(saved.getValue().getUpdatedAt()).isNotNull();

        verify(tokenRedisRepository).deleteRefreshToken("pwd_reset:test@gmail.com"); // código de un solo uso
        verify(tokenRedisRepository).deleteRefreshToken("test@gmail.com"); // sesiones abiertas
        verify(passwordResetAttemptService).reset("test@gmail.com");
        verify(resendEmailService).sendPasswordChangedEmail("test@gmail.com");
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq("test@gmail.com"),
                argThat((SecurityAuditEvent e) -> e.action().equals("PASSWORD_RESET_COMPLETED")
                        && "127.0.0.1".equals(e.ipAddress())));
    }

    @Test
    void execute_deberiaRechazarElCodigo_cuandoNoCoincide() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("pwd_reset:test@gmail.com")).thenReturn("999999");
        when(passwordResetAttemptService.registerFailureAndCheckLimit("test@gmail.com")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> resetPasswordUseCase.execute(request, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código inválido o expirado");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, resendEmailService, eventPublisher);
    }

    @Test
    void execute_deberiaRechazarElCodigo_cuandoYaExpiroONuncaSePidio() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("pwd_reset:test@gmail.com")).thenReturn(null);
        when(passwordResetAttemptService.registerFailureAndCheckLimit("test@gmail.com")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> resetPasswordUseCase.execute(request, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código inválido o expirado");
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_deberiaInvalidarElCodigoYPedirUnoNuevo_cuandoSeAlcanzaElMaximoDeIntentos() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("pwd_reset:test@gmail.com")).thenReturn("999999");
        when(passwordResetAttemptService.registerFailureAndCheckLimit("test@gmail.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> resetPasswordUseCase.execute(request, "127.0.0.1"))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessageContaining("Solicita un nuevo código");
        verify(tokenRedisRepository).deleteRefreshToken("pwd_reset:test@gmail.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_deberiaLanzarNotFound_cuandoElCodigoEsValidoPeroLaCuentaYaNoExiste() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("pwd_reset:test@gmail.com")).thenReturn("123456");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> resetPasswordUseCase.execute(request, "127.0.0.1"))
                .isInstanceOf(NotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_noDeberiaFallar_cuandoNoSePuedeEnviarElAvisoDeCambio() {
        // Arrange: el aviso es best-effort, la contraseña ya cambió
        User user = User.builder().email("test@gmail.com").passwordHash("hash-viejo").build();
        when(tokenRedisRepository.getRefreshToken("pwd_reset:test@gmail.com")).thenReturn("123456");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NuevaClave1!")).thenReturn("hash-nuevo");
        doThrow(new ExternalServiceException("Error al enviar la notificación de cambio de contraseña"))
                .when(resendEmailService).sendPasswordChangedEmail("test@gmail.com");

        // Act & Assert
        assertThatCode(() -> resetPasswordUseCase.execute(request, "127.0.0.1")).doesNotThrowAnyException();
        verify(userRepository).save(user);
    }
}