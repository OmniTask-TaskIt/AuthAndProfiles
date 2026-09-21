package com.omnitask.AuthAndProfiles.domain.ports.out.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * Marca en Redis a los usuarios cuyos access tokens ya emitidos deben rechazarse
 * (cuenta suspendida o bloqueada). El TTL de la marca es la vida máxima de un access token,
 * pasado ese tiempo todos los tokens anteriores ya expiraron por sí solos.
 */
@Repository
@RequiredArgsConstructor
public class AccessRevocationRepository {

    private static final String PREFIX = "revoked_user:";

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
}
