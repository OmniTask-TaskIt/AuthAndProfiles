package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
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
 * RF-AUTH-8: cierre de sesión explícito. Borra el refresh token (ya no se puede renovar la sesión)
 * y agrega el access token a la lista de revocación hasta su expiración, de modo que deje de ser
 * válido para futuras peticiones aunque aún no haya vencido.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCase {

    private final JwtService jwtService;
    private final TokenRedisRepository tokenRedisRepository;
    private final AccessRevocationRepository accessRevocationRepository;
    private final EventPublisher eventPublisher;

    public void execute(String email, String accessToken, String clientIp) {
        tokenRedisRepository.deleteRefreshToken(email);

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