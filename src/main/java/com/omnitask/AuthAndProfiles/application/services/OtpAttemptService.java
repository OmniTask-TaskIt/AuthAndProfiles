package com.omnitask.AuthAndProfiles.application.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Limita los intentos fallidos de verificación de OTP por correo para evitar fuerza bruta
 * sobre el código de 6 dígitos (PBI 1.2: "verify-otp con control de intentos").
 */
@Service
@RequiredArgsConstructor
public class OtpAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final long WINDOW_SECONDS = 600;

    private final StringRedisTemplate redisTemplate;

    /**
     * Registra un intento fallido.
     *
     * @return true si con este intento se alcanzó el máximo permitido
     */
    public boolean registerFailureAndCheckLimit(String email) {
        String key = key(email);
        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        return attempts != null && attempts >= MAX_ATTEMPTS;
    }

    public void reset(String email) {
        redisTemplate.delete(key(email));
    }

    private String key(String email) {
        return "otp_attempts:" + email;
    }
}
