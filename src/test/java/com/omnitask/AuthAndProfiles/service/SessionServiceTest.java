package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.ClientContext;
import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.application.services.SessionTokens;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.models.UserSession;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.AccessRevocationRepository;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserSessionRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final String EMAIL = "test@gmail.com";
    private static final String CHROME_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0 Safari/537.36";

    @Mock
    private UserSessionRepository sessionRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private AccessRevocationRepository accessRevocationRepository;
    @Mock
    private ResendEmailService resendEmailService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SessionService sessionService;

    private User user;

    @BeforeEach
    void setUp() {
        // maxDevices es un campo @Value: en un test unitario (sin Spring) se inyecta por reflexión
        ReflectionTestUtils.setField(sessionService, "maxDevices", 3);
        user = User.builder().email(EMAIL).role(Role.SEEKER).build();
    }

    private void stubTokens() {
        when(jwtService.getRefreshTokenExpirationMillis()).thenReturn(604800000L);
        when(jwtService.generateAccessToken(eq(EMAIL), eq("SEEKER"), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(EMAIL), anyString())).thenReturn("refresh-token");
    }

    private UserSession activeSession(String id, String deviceId, int minutesAgo) {
        return UserSession.builder().id(id).userEmail(EMAIL).deviceId(deviceId).deviceInfo("Chrome en Windows")
                .ipAddress("198.51.100.7").lastActiveAt(LocalDateTime.now().minusMinutes(minutesAgo))
                .expiresAt(LocalDateTime.now().plusDays(1)).revoked(false).build();
    }

    private void stubActiveSessions(UserSession... sessions) {
        when(sessionRepository.findByUserEmailAndRevokedFalseAndExpiresAtAfterOrderByLastActiveAtAsc(eq(EMAIL), any()))
                .thenReturn(List.of(sessions));
    }

    /** La sesión nueva es siempre la última que se guarda (las revocaciones se guardan antes). */
    private UserSession lastSavedSession() {
        ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
        verify(sessionRepository, atLeastOnce()).save(captor.capture());
        List<UserSession> saved = captor.getAllValues();
        return saved.get(saved.size() - 1);
    }

    @Test
    void openSession_deberiaCrearLaSesionYEmitirLosTokensDeLaSesion_enElPrimerInicio() {
        // Arrange
        stubTokens();
        ClientContext context = new ClientContext("203.0.113.5", CHROME_WINDOWS, "device-1");

        // Act
        SessionTokens tokens = sessionService.openSession(user, context);

        // Assert
        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");

        UserSession session = lastSavedSession();
        assertThat(session.getId()).isNotBlank();
        assertThat(session.getUserEmail()).isEqualTo(EMAIL);
        assertThat(session.getDeviceId()).isEqualTo("device-1");
        assertThat(session.getDeviceInfo()).isEqualTo("Chrome en Windows");
        assertThat(session.getIpAddress()).isEqualTo("203.0.113.5");
        assertThat(session.isRevoked()).isFalse();
        assertThat(session.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));

        // Los tokens se firman con el id de la sesión recién creada
        verify(jwtService).generateAccessToken(EMAIL, "SEEKER", session.getId());
        verify(jwtService).generateRefreshToken(EMAIL, session.getId());
        verify(tokenRedisRepository).saveRefreshToken(EMAIL, "refresh-token", 604800000L);
        // Primer inicio de sesión de la cuenta: no hay con qué comparar, no es sospechoso
        verify(resendEmailService, never()).sendSuspiciousLoginEmail(any(), any(), any(), any());
    }

    @Test
    void openSession_deberiaAvisarPorCorreo_cuandoLaCuentaTieneHistorialYElDispositivoEsNuevo() {
        // Arrange
        stubTokens();
        when(sessionRepository.existsByUserEmail(EMAIL)).thenReturn(true);
        when(sessionRepository.existsByUserEmailAndDeviceId(EMAIL, "device-nuevo")).thenReturn(false);

        // Act
        sessionService.openSession(user, new ClientContext("203.0.113.5", CHROME_WINDOWS, "device-nuevo"));

        // Assert
        verify(resendEmailService).sendSuspiciousLoginEmail(eq(EMAIL), eq("Chrome en Windows"), eq("203.0.113.5"),
                anyString());
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq(EMAIL),
                argThat((SecurityAuditEvent e) -> e.action().equals("SUSPICIOUS_LOGIN")
                        && "203.0.113.5".equals(e.ipAddress())));
    }

    @Test
    void openSession_noDeberiaAvisar_cuandoElDispositivoYaHabiaIniciadoSesion() {
        // Arrange
        stubTokens();
        when(sessionRepository.existsByUserEmail(EMAIL)).thenReturn(true);
        when(sessionRepository.existsByUserEmailAndDeviceId(EMAIL, "device-1")).thenReturn(true);

        // Act
        sessionService.openSession(user, new ClientContext("203.0.113.5", CHROME_WINDOWS, "device-1"));

        // Assert
        verify(resendEmailService, never()).sendSuspiciousLoginEmail(any(), any(), any(), any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void openSession_deberiaContinuar_cuandoFallaElEnvioDelAviso() {
        // Arrange: el aviso es best-effort
        stubTokens();
        when(sessionRepository.existsByUserEmail(EMAIL)).thenReturn(true);
        doThrow(new RuntimeException("Resend caído")).when(resendEmailService)
                .sendSuspiciousLoginEmail(any(), any(), any(), any());

        // Act
        SessionTokens tokens = sessionService.openSession(user,
                new ClientContext("203.0.113.5", CHROME_WINDOWS, "device-nuevo"));

        // Assert
        assertThat(tokens.accessToken()).isEqualTo("access-token");
        verify(tokenRedisRepository).saveRefreshToken(EMAIL, "refresh-token", 604800000L);
    }

    @Test
    void openSession_deberiaReemplazarLaSesionAnterior_cuandoElDispositivoEsElMismo() {
        // Arrange
        stubTokens();
        when(jwtService.getAccessTokenExpirationMillis()).thenReturn(900000L);
        UserSession anterior = activeSession("sesion-vieja", "device-1", 30);
        UserSession otroDispositivo = activeSession("sesion-otro", "device-2", 10);
        stubActiveSessions(anterior, otroDispositivo);

        // Act
        sessionService.openSession(user, new ClientContext("203.0.113.5", CHROME_WINDOWS, "device-1"));

        // Assert: solo se cierra la del mismo dispositivo y su access token deja de valer de inmediato
        assertThat(anterior.isRevoked()).isTrue();
        assertThat(anterior.getRevokedReason()).isEqualTo("REPLACED_SAME_DEVICE");
        assertThat(anterior.getRevokedAt()).isNotNull();
        assertThat(otroDispositivo.isRevoked()).isFalse();
        verify(accessRevocationRepository).revokeSession("sesion-vieja", 900000L);
        verify(accessRevocationRepository, never()).revokeSession(eq("sesion-otro"), anyLong());
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq(EMAIL),
                argThat((SecurityAuditEvent e) -> e.action().equals("SESSION_REVOKED_REPLACED_SAME_DEVICE")));
    }

    @Test
    void openSession_deberiaCerrarLaSesionMenosReciente_cuandoSeSuperaElLimiteDeDispositivos() {
        // Arrange: límite de 2 dispositivos y ya hay 2 activos (la lista llega de la menos a la más reciente)
        ReflectionTestUtils.setField(sessionService, "maxDevices", 2);
        stubTokens();
        when(jwtService.getAccessTokenExpirationMillis()).thenReturn(900000L);
        UserSession masAntigua = activeSession("sesion-antigua", "device-a", 60);
        UserSession reciente = activeSession("sesion-reciente", "device-b", 5);
        stubActiveSessions(masAntigua, reciente);

        // Act
        sessionService.openSession(user, new ClientContext("203.0.113.5", CHROME_WINDOWS, "device-c"));

        // Assert
        assertThat(masAntigua.isRevoked()).isTrue();
        assertThat(masAntigua.getRevokedReason()).isEqualTo("DEVICE_LIMIT");
        assertThat(reciente.isRevoked()).isFalse();
        verify(accessRevocationRepository).revokeSession("sesion-antigua", 900000L);
    }

    @Test
    void openSession_noDeberiaCerrarNada_cuandoAunHayCupoDeDispositivos() {
        // Arrange
        stubTokens();
        stubActiveSessions(activeSession("sesion-a", "device-a", 60), activeSession("sesion-b", "device-b", 5));

        // Act
        sessionService.openSession(user, new ClientContext("203.0.113.5", CHROME_WINDOWS, "device-c"));

        // Assert: 2 activas con límite 3 -> la nueva es la tercera
        verify(accessRevocationRepository, never()).revokeSession(anyString(), anyLong());
    }

    @Test
    void openSession_deberiaTratarUnLimiteInvalidoComoUnDispositivo() {
        // Arrange
        ReflectionTestUtils.setField(sessionService, "maxDevices", 0);
        stubTokens();
        when(jwtService.getAccessTokenExpirationMillis()).thenReturn(900000L);
        UserSession existente = activeSession("sesion-a", "device-a", 60);
        stubActiveSessions(existente);

        // Act
        sessionService.openSession(user, new ClientContext("203.0.113.5", CHROME_WINDOWS, "device-b"));

        // Assert
        assertThat(existente.isRevoked()).isTrue();
        assertThat(existente.getRevokedReason()).isEqualTo("DEVICE_LIMIT");
    }

    @ParameterizedTest
    @CsvSource({
            "Mozilla Edg/120 Windows NT 10.0, Edge en Windows",
            "Mozilla OPR/90 Mac OS X 10_15, Opera en macOS",
            "Mozilla Firefox/120 X11; Linux x86_64, Firefox en Linux",
            "Mozilla Chrome/120 Safari/537 Android 14, Chrome en Android",
            "Mozilla Version/17 Safari/605 iPhone OS 17, Safari en iOS",
            "Mozilla Version/17 Safari/605 iPad OS 17, Safari en iOS",
            "curl/8.0, Navegador desconocido en sistema desconocido"
    })
    void openSession_deberiaDescribirElDispositivoAPartirDelUserAgent(String userAgent, String esperado) {
        stubTokens();

        sessionService.openSession(user, new ClientContext("203.0.113.5", userAgent, "device-1"));

        assertThat(lastSavedSession().getDeviceInfo()).isEqualTo(esperado);
    }

    @Test
    void openSession_deberiaUsarUnaDescripcionGenerica_cuandoNoHayUserAgent() {
        stubTokens();

        sessionService.openSession(user, new ClientContext("203.0.113.5", null, "device-1"));
        assertThat(lastSavedSession().getDeviceInfo()).isEqualTo("Dispositivo desconocido");

        sessionService.openSession(user, new ClientContext("203.0.113.5", "   ", "device-1"));
        assertThat(lastSavedSession().getDeviceInfo()).isEqualTo("Dispositivo desconocido");
    }

    @Test
    void openSession_deberiaDerivarElIdDelDispositivoDelUserAgent_cuandoElFrontNoLoEnvia() {
        stubTokens();

        sessionService.openSession(user, new ClientContext("203.0.113.5", CHROME_WINDOWS, null));
        assertThat(lastSavedSession().getDeviceId()).isEqualTo("ua:Chrome en Windows");

        sessionService.openSession(user, new ClientContext("203.0.113.5", CHROME_WINDOWS, "  "));
        assertThat(lastSavedSession().getDeviceId()).isEqualTo("ua:Chrome en Windows");
    }

    @Test
    void openSession_deberiaLimpiarYAcotarElIdDelDispositivo() {
        stubTokens();

        sessionService.openSession(user, new ClientContext("203.0.113.5", CHROME_WINDOWS, "  device-1  "));
        assertThat(lastSavedSession().getDeviceId()).isEqualTo("device-1");

        sessionService.openSession(user, new ClientContext("203.0.113.5", CHROME_WINDOWS, "x".repeat(100)));
        assertThat(lastSavedSession().getDeviceId()).hasSize(64);
    }

    @Test
    void findActive_deberiaRetornarLaSesion_soloSiNoEstaRevocadaNiVencida() {
        UserSession vigente = activeSession("vigente", "device-1", 1);
        UserSession revocada = activeSession("revocada", "device-1", 1);
        revocada.setRevoked(true);
        UserSession vencida = activeSession("vencida", "device-1", 1);
        vencida.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(sessionRepository.findByIdAndUserEmail("vigente", EMAIL)).thenReturn(Optional.of(vigente));
        when(sessionRepository.findByIdAndUserEmail("revocada", EMAIL)).thenReturn(Optional.of(revocada));
        when(sessionRepository.findByIdAndUserEmail("vencida", EMAIL)).thenReturn(Optional.of(vencida));
        when(sessionRepository.findByIdAndUserEmail("ajena", EMAIL)).thenReturn(Optional.empty());

        assertThat(sessionService.findActive(EMAIL, "vigente")).contains(vigente);
        assertThat(sessionService.findActive(EMAIL, "revocada")).isEmpty();
        assertThat(sessionService.findActive(EMAIL, "vencida")).isEmpty();
        assertThat(sessionService.findActive(EMAIL, "ajena")).isEmpty();
    }

    @Test
    void listActive_deberiaRetornarLasSesionesVigentesDelUsuario() {
        UserSession sesion = activeSession("sesion-a", "device-a", 1);
        stubActiveSessions(sesion);

        assertThat(sessionService.listActive(EMAIL)).containsExactly(sesion);
    }

    @Test
    void touch_deberiaActualizarLaUltimaActividadYGuardar() {
        UserSession sesion = activeSession("sesion-a", "device-a", 120);
        LocalDateTime antes = sesion.getLastActiveAt();

        sessionService.touch(sesion);

        assertThat(sesion.getLastActiveAt()).isAfter(antes);
        verify(sessionRepository).save(sesion);
    }

    @Test
    void revoke_deberiaCerrarLaSesionRevocarSuAccessTokenYAuditar() {
        // Arrange
        when(jwtService.getAccessTokenExpirationMillis()).thenReturn(900000L);
        UserSession sesion = activeSession("sesion-a", "device-a", 5);
        when(sessionRepository.findByIdAndUserEmail("sesion-a", EMAIL)).thenReturn(Optional.of(sesion));

        // Act
        boolean resultado = sessionService.revoke(EMAIL, "sesion-a", "REMOTE_LOGOUT");

        // Assert
        assertThat(resultado).isTrue();
        assertThat(sesion.isRevoked()).isTrue();
        assertThat(sesion.getRevokedReason()).isEqualTo("REMOTE_LOGOUT");
        verify(sessionRepository).save(sesion);
        verify(accessRevocationRepository).revokeSession("sesion-a", 900000L);
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq(EMAIL),
                argThat((SecurityAuditEvent e) -> e.action().equals("SESSION_REVOKED_REMOTE_LOGOUT")));
    }

    @Test
    void revoke_deberiaRetornarFalse_cuandoLaSesionNoExisteONoEsDelUsuario() {
        when(sessionRepository.findByIdAndUserEmail("ajena", EMAIL)).thenReturn(Optional.empty());

        assertThat(sessionService.revoke(EMAIL, "ajena", "REMOTE_LOGOUT")).isFalse();
        verify(sessionRepository, never()).save(any());
        verify(accessRevocationRepository, never()).revokeSession(anyString(), anyLong());
    }

    @Test
    void revoke_noDeberiaHacerNada_cuandoLaSesionYaEstabaCerrada() {
        UserSession sesion = activeSession("sesion-a", "device-a", 5);
        sesion.setRevoked(true);
        when(sessionRepository.findByIdAndUserEmail("sesion-a", EMAIL)).thenReturn(Optional.of(sesion));

        assertThat(sessionService.revoke(EMAIL, "sesion-a", "REMOTE_LOGOUT")).isTrue();
        verify(sessionRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }
}
