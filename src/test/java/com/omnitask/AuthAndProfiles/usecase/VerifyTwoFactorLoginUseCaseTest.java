package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.ClientContext;
import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.application.services.SessionTokens;
import com.omnitask.AuthAndProfiles.application.services.TwoFactorService;
import com.omnitask.AuthAndProfiles.application.usecases.VerifyTwoFactorLoginUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.AccountRestrictedException;
import com.omnitask.AuthAndProfiles.domain.exceptions.AuthenticationFailedException;
import com.omnitask.AuthAndProfiles.domain.exceptions.TooManyAttemptsException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.TwoFactorLoginRequestDTO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerifyTwoFactorLoginUseCaseTest {

    private static final ClientContext CONTEXT = new ClientContext("127.0.0.1", "Mozilla/5.0 Chrome/120", "device-1");

    @Mock
    private TwoFactorService twoFactorService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private VerifyTwoFactorLoginUseCase useCase;

    private TwoFactorLoginRequestDTO request;

    @BeforeEach
    void setUp() {
        request = new TwoFactorLoginRequestDTO();
        request.setChallengeId("challenge-1");
        request.setCode("123456");
    }

    @Test
    void execute_deberiaAbrirLaSesionYEmitirTokens_conElCodigoCorrecto() {
        // Arrange
        User user = User.builder().email("test@gmail.com").role(Role.SEEKER).twoFactorEnabled(true).build();
        when(twoFactorService.verifyLoginChallenge("challenge-1", "123456")).thenReturn("test@gmail.com");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(sessionService.openSession(user, CONTEXT)).thenReturn(new SessionTokens("access-token", "refresh-token"));

        // Act
        AuthResponseDTO response = useCase.execute(request, CONTEXT);

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getEmail()).isEqualTo("test@gmail.com");
        assertThat(response.isTwoFactorRequired()).isFalse();
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq("test@gmail.com"),
                argThat((SecurityAuditEvent e) -> e.action().equals("LOGIN_SUCCESS") && "127.0.0.1".equals(e.ipAddress())));
    }

    @Test
    void execute_noDeberiaAbrirSesion_cuandoElCodigoEsIncorrecto() {
        when(twoFactorService.verifyLoginChallenge("challenge-1", "123456"))
                .thenThrow(new IllegalArgumentException("Código inválido o expirado"));

        assertThatThrownBy(() -> useCase.execute(request, CONTEXT)).isInstanceOf(IllegalArgumentException.class);
        verify(sessionService, never()).openSession(any(), any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void execute_deberiaPropagarElBloqueoPorDemasiadosIntentos() {
        when(twoFactorService.verifyLoginChallenge("challenge-1", "123456"))
                .thenThrow(new TooManyAttemptsException("Demasiados intentos fallidos."));

        assertThatThrownBy(() -> useCase.execute(request, CONTEXT)).isInstanceOf(TooManyAttemptsException.class);
        verify(sessionService, never()).openSession(any(), any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoLaCuentaYaNoExiste() {
        when(twoFactorService.verifyLoginChallenge("challenge-1", "123456")).thenReturn("test@gmail.com");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(request, CONTEXT))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(sessionService, never()).openSession(any(), any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoLaCuentaSeSuspendioEntreLosDosPasos() {
        User user = User.builder().email("test@gmail.com").role(Role.SEEKER).twoFactorEnabled(true)
                .accountStatus(AccountStatus.BLOCKED).build();
        when(twoFactorService.verifyLoginChallenge("challenge-1", "123456")).thenReturn("test@gmail.com");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(request, CONTEXT)).isInstanceOf(AccountRestrictedException.class);
        verify(sessionService, never()).openSession(any(), any());
    }
}
