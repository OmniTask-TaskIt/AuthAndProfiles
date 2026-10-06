package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.application.usecases.LogoutUseCase;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.AccessRevocationRepository;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private AccessRevocationRepository accessRevocationRepository;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private SessionService sessionService;

    @InjectMocks
    private LogoutUseCase logoutUseCase;

    @Test
    void execute_deberiaBorrarElRefreshTokenYRevocarElAccessTokenHastaQueExpire() {
        // Arrange: al access token le quedan ~10 minutos de vida
        when(jwtService.<Date>extractClaim(eq("access-token"), any()))
                .thenReturn(new Date(System.currentTimeMillis() + 600_000L));

        // Act
        logoutUseCase.execute("test@gmail.com", "access-token", "127.0.0.1");

        // Assert
        verify(tokenRedisRepository).deleteRefreshToken("test@gmail.com");

        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(accessRevocationRepository).revokeToken(eq("access-token"), ttl.capture());
        assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(600_000L);

        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq("test@gmail.com"),
                argThat((SecurityAuditEvent e) -> e.action().equals("LOGOUT") && "127.0.0.1".equals(e.ipAddress())));
    }

    @Test
    void execute_noDeberiaRevocarElAccessToken_cuandoYaExpiro() {
        // Arrange
        when(jwtService.<Date>extractClaim(eq("access-token"), any()))
                .thenReturn(new Date(System.currentTimeMillis() - 1_000L));

        // Act
        logoutUseCase.execute("test@gmail.com", "access-token", "127.0.0.1");

        // Assert: el refresh token se borra igual, pero no hay marca que guardar en Redis
        verify(tokenRedisRepository).deleteRefreshToken("test@gmail.com");
        verify(accessRevocationRepository, never()).revokeToken(anyString(), anyLong());
    }

    @Test
    void execute_deberiaCerrarSoloLaSesionDelToken_sinBorrarElRefreshTokenDeLaCuenta() {
        // Arrange: el access token pertenece a una sesión (claim "sid")
        when(jwtService.extractSessionId("access-token")).thenReturn("sesion-1");
        when(jwtService.<Date>extractClaim(eq("access-token"), any()))
                .thenReturn(new Date(System.currentTimeMillis() + 600_000L));

        // Act
        logoutUseCase.execute("test@gmail.com", "access-token", "127.0.0.1");

        // Assert: las demás sesiones del usuario siguen intactas
        verify(sessionService).revoke("test@gmail.com", "sesion-1", "LOGOUT");
        verify(tokenRedisRepository, never()).deleteRefreshToken(anyString());
        verify(accessRevocationRepository).revokeToken(eq("access-token"), anyLong());
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq("test@gmail.com"),
                argThat((SecurityAuditEvent e) -> e.action().equals("LOGOUT")));
    }

    @Test
    void execute_noDeberiaTocarLasSesiones_cuandoElTokenNoTieneSesion() {
        // Arrange: token anterior a las sesiones
        when(jwtService.extractSessionId("access-token")).thenReturn(null);
        when(jwtService.<Date>extractClaim(eq("access-token"), any()))
                .thenReturn(new Date(System.currentTimeMillis() + 600_000L));

        // Act
        logoutUseCase.execute("test@gmail.com", "access-token", "127.0.0.1");

        // Assert
        verify(sessionService, never()).revoke(anyString(), anyString(), anyString());
        verify(tokenRedisRepository).deleteRefreshToken("test@gmail.com");
    }
}
