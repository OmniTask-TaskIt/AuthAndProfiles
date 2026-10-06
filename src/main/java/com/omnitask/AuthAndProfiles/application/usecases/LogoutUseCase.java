package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.AccessRevocationRepository;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

/**
 * RF-AUTH-8: cierre de sesión explícito. Cierra la sesión del dispositivo (su refresh token ya no sirve y las demás
 * sesiones del usuario siguen intactas, RF-AUTH-10) y agrega el access token a la lista de revocación hasta su
 * expiración, de modo que deje de ser válido para futuras peticiones aunque aún no haya vencido. Un token anterior a
 * las sesiones (sin "sid") conserva el comportamiento previo: borra el refresh token de la cuenta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCase {

    private final JwtService jwtService;
    private final TokenRedisRepository tokenRedisRepository;
    private final AccessRevocationRepository accessRevocationRepository;
    private final EventPublisher eventPublisher;
    private final SessionService sessionService;

    public void execute(String email, String accessToken, String clientIp) {
        String sessionId = jwtService.extractSessionId(accessToken);
        if (sessionId != null) {
            sessionService.revoke(email, sessionId, "LOGOUT");
        } else {
            tokenRedisRepository.deleteRefreshToken(email);
        }

        Date expiration = jwtService.extractClaim(accessToken, Claims::getExpiration);
        long remainingMillis = expiration.getTime() - System.currentTimeMillis();
        // Si ya venció no hace falta revocarlo: Redis solo guarda marcas con TTL positivo.
        if (remainingMillis > 0) {
            accessRevocationRepository.revokeToken(accessToken, remainingMillis);
        }

        eventPublisher.publish(EventType.SECURITY_AUDIT, email,
                new SecurityAuditEvent("LOGOUT", email, null, clientIp, Instant.now()));
        log.info("[AUDIT] [SEC-AUTH-07] Sesión cerrada y token revocado para: {}", email);
    }
}