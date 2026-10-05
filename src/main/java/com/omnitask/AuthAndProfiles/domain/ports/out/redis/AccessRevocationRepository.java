package com.omnitask.AuthAndProfiles.domain.ports.out.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Revocación de access tokens en Redis, de dos formas:
 * <ul>
 * <li><b>Por usuario</b>: marca a los usuarios cuyos access tokens ya emitidos deben rechazarse
 * (cuenta suspendida o bloqueada). El TTL de la marca es la vida máxima de un access token,
 * pasado ese tiempo todos los tokens anteriores ya expiraron por sí solos.</li>
 * <li><b>Por token</b>: lista de revocación (blacklist) de un access token puntual, usada en el cierre
 * de sesión (RF-AUTH-8). Se guarda el hash SHA-256 del token, nunca el token en claro, con un TTL igual
 * al tiempo que le queda de vida: después de eso el token expira solo y la marca se limpia sola.</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class AccessRevocationRepository {

    private static final String PREFIX = "revoked_user:";
    private static final String TOKEN_PREFIX = "revoked_token:";

    private final StringRedisTemplate redisTemplate;

    public void revokeUser(String email, long ttlMillis) {
        redisTemplate.opsForValue().set(PREFIX + email, "REVOKED", Duration.ofMillis(ttlMillis));
    }

    public boolean isUserRevoked(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + email));
    }

    public void clearUserRevocation(String email) {
        redisTemplate.delete(PREFIX + email);
    }

    /** Agrega un access token a la lista de revocación hasta que expire por sí solo. */
    public void revokeToken(String token, long ttlMillis) {
        redisTemplate.opsForValue().set(TOKEN_PREFIX + sha256(token), "REVOKED", Duration.ofMillis(ttlMillis));
    }

    public boolean isTokenRevoked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_PREFIX + sha256(token)));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 está garantizado por todas las JVM; no debería ocurrir.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}