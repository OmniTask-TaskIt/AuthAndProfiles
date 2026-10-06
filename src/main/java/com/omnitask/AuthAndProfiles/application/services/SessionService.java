package com.omnitask.AuthAndProfiles.application.services;

import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.models.UserSession;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.AccessRevocationRepository;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserSessionRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gestión de sesiones por dispositivo:
 * <ul>
 * <li>RF-AUTH-10: cada inicio de sesión crea una {@link UserSession} cuyo id viaja en el claim "sid" de los tokens;
 * se pueden listar y cerrar de forma remota, y al cerrarlas su access token deja de valer de inmediato.</li>
 * <li>RF-AUTH-13: máximo de dispositivos simultáneos (app.sessions.max-devices). Un nuevo inicio de sesión desde
 * el mismo dispositivo reemplaza la sesión anterior; si se supera el límite se cierra la menos reciente (LRU).</li>
 * <li>RF-AUTH-11: si la cuenta ya tenía historial y el dispositivo nunca se había usado, se avisa por correo.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final DateTimeFormatter EMAIL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UserSessionRepository sessionRepository;
    private final JwtService jwtService;
    private final TokenRedisRepository tokenRedisRepository;
    private final AccessRevocationRepository accessRevocationRepository;
    private final ResendEmailService resendEmailService;
    private final EventPublisher eventPublisher;

    @Value("${app.sessions.max-devices:3}")
    private int maxDevices;

    /** Abre una sesión nueva para el usuario y emite sus tokens. */
    public SessionTokens openSession(User user, ClientContext context) {
        String email = user.getEmail();
        LocalDateTime now = LocalDateTime.now();
        String deviceId = resolveDeviceId(context);
        String deviceInfo = describeDevice(context.userAgent());

        boolean hasHistory = sessionRepository.existsByUserEmail(email);
        boolean knownDevice = sessionRepository.existsByUserEmailAndDeviceId(email, deviceId);

        List<UserSession> active = new ArrayList<>(
                sessionRepository.findByUserEmailAndRevokedFalseAndExpiresAtAfterOrderByLastActiveAtAsc(email, now));

        // Un dispositivo, una sesión: iniciar de nuevo en el mismo dispositivo reemplaza la anterior.
        active.removeIf(session -> {
            boolean sameDevice = deviceId.equals(session.getDeviceId());
            if (sameDevice) {
                revokeSession(session, "REPLACED_SAME_DEVICE");
            }
            return sameDevice;
        });

        // Límite de dispositivos (RF-AUTH-13): se cierra la sesión menos reciente hasta dejar un cupo libre.
        int limit = Math.max(1, maxDevices);
        while (active.size() >= limit) {
            revokeSession(active.remove(0), "DEVICE_LIMIT");
        }

        String sessionId = UUID.randomUUID().toString();
        long refreshTtl = jwtService.getRefreshTokenExpirationMillis();
        sessionRepository.save(UserSession.builder()
                .id(sessionId)
                .userEmail(email)
                .deviceId(deviceId)
                .deviceInfo(deviceInfo)
                .ipAddress(context.ip())
                .createdAt(now)
                .lastActiveAt(now)
                .expiresAt(now.plus(Duration.ofMillis(refreshTtl)))
                .revoked(false)
                .build());

        String accessToken = jwtService.generateAccessToken(email, user.getRole().name(), sessionId);
        String refreshToken = jwtService.generateRefreshToken(email, sessionId);
        // Marcador global de "esta cuenta tiene sesión": restablecer contraseña, eliminar o suspender la cuenta lo borran.
        tokenRedisRepository.saveRefreshToken(email, refreshToken, refreshTtl);

        if (hasHistory && !knownDevice) {
            notifySuspiciousLogin(email, deviceInfo, context.ip(), now);
        }
        return new SessionTokens(accessToken, refreshToken);
    }

    /** Sesión vigente (no revocada ni vencida) del usuario, si existe. */
    public Optional<UserSession> findActive(String email, String sessionId) {
        return sessionRepository.findByIdAndUserEmail(sessionId, email)
                .filter(session -> !session.isRevoked() && session.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    public List<UserSession> listActive(String email) {
        return sessionRepository.findByUserEmailAndRevokedFalseAndExpiresAtAfterOrderByLastActiveAtAsc(email,
                LocalDateTime.now());
    }

    /** Marca la sesión como usada hace un momento (se llama al renovar el access token). */
    public void touch(UserSession session) {
        session.setLastActiveAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    /**
     * Cierra una sesión del usuario. Devuelve false si no existe (o no es suya); si ya estaba cerrada no hace
     * nada y devuelve true.
     */
    public boolean revoke(String email, String sessionId, String reason) {
        Optional<UserSession> found = sessionRepository.findByIdAndUserEmail(sessionId, email);
        if (found.isEmpty()) {
            return false;
        }
        if (!found.get().isRevoked()) {
            revokeSession(found.get(), reason);
        }
        return true;
    }

    private void revokeSession(UserSession session, String reason) {
        session.setRevoked(true);
        session.setRevokedAt(LocalDateTime.now());
        session.setRevokedReason(reason);
        sessionRepository.save(session);
        // El access token de esa sesión deja de valer ya, sin esperar a que expire.
        accessRevocationRepository.revokeSession(session.getId(), jwtService.getAccessTokenExpirationMillis());

        eventPublisher.publish(EventType.SECURITY_AUDIT, session.getUserEmail(),
                new SecurityAuditEvent("SESSION_REVOKED_" + reason, session.getUserEmail(), null,
                        session.getIpAddress(), Instant.now()));
        log.info("[AUDIT] [SEC-AUTH-08] Sesión {} de {} cerrada ({})", session.getId(), session.getUserEmail(), reason);
    }

    /** El aviso es best-effort: si Resend falla, el inicio de sesión sigue adelante. */
    private void notifySuspiciousLogin(String email, String deviceInfo, String ip, LocalDateTime when) {
        eventPublisher.publish(EventType.SECURITY_AUDIT, email,
                new SecurityAuditEvent("SUSPICIOUS_LOGIN", email, null, ip, Instant.now()));
        log.warn("[SECURITY] [SEC-AUTH-09] Inicio de sesión desde un dispositivo no reconocido para {}: {} ({})",
                email, deviceInfo, ip);
        try {
            resendEmailService.sendSuspiciousLoginEmail(email, deviceInfo, ip, EMAIL_DATE.format(when));
        } catch (RuntimeException e) {
            log.warn("[AUDIT] No se pudo enviar el aviso de inicio de sesión sospechoso a {}: {}", email,
                    e.getMessage());
        }
    }

    private static String resolveDeviceId(ClientContext context) {
        String provided = context.deviceId();
        if (provided != null && !provided.isBlank()) {
            String trimmed = provided.trim();
            return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
        }
        return "ua:" + describeDevice(context.userAgent());
    }

    /** Resume el User-Agent en algo legible, por ejemplo "Chrome en Windows". */
    private static String describeDevice(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Dispositivo desconocido";
        }
        String browser;
        if (userAgent.contains("Edg/")) {
            browser = "Edge";
        } else if (userAgent.contains("OPR/")) {
            browser = "Opera";
        } else if (userAgent.contains("Firefox/")) {
            browser = "Firefox";
        } else if (userAgent.contains("Chrome/")) {
            browser = "Chrome";
        } else if (userAgent.contains("Safari/")) {
            browser = "Safari";
        } else {
            browser = "Navegador desconocido";
        }

        String os;
        // Android incluye "Linux" y iOS incluye "Mac OS X" en su User-Agent: se evalúan primero.
        if (userAgent.contains("Android")) {
            os = "Android";
        } else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
            os = "iOS";
        } else if (userAgent.contains("Windows")) {
            os = "Windows";
        } else if (userAgent.contains("Mac OS X")) {
            os = "macOS";
        } else if (userAgent.contains("Linux")) {
            os = "Linux";
        } else {
            os = "sistema desconocido";
        }
        return browser + " en " + os;
    }
}
