package com.omnitask.AuthAndProfiles.application.services;

import com.omnitask.AuthAndProfiles.domain.enums.TwoFactorAction;
import com.omnitask.AuthAndProfiles.domain.exceptions.TooManyAttemptsException;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Segundo factor por correo (RF-AUTH-9). Emite y verifica los códigos de 6 dígitos en dos situaciones:
 * <ul>
 * <li><b>Ajustes</b>: activar o desactivar el 2FA desde la cuenta (10 minutos de vigencia).</li>
 * <li><b>Inicio de sesión</b>: tras validar la contraseña (o Google/GitHub) se abre un reto con un challengeId
 * aleatorio y un código con 5 minutos de vigencia; solo quien tiene el challengeId puede canjearlo.</li>
 * </ul>
 * Cada código sirve una sola vez y se invalida a los 5 fallos (mismo límite que el OTP de registro).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    static final int SETTINGS_CODE_MINUTES = 10;
    static final int LOGIN_CODE_MINUTES = 5;

    private final TokenRedisRepository tokenRedisRepository;
    private final OtpGenerator otpGenerator;
    private final OtpAttemptService otpAttemptService;
    private final ResendEmailService resendEmailService;

    /** Envía el código para activar o desactivar el 2FA de la cuenta. */
    public void sendSettingsCode(String email, TwoFactorAction action) {
        issueCode(settingsKey(email, action), email, SETTINGS_CODE_MINUTES);
        log.info("[AUDIT] [SEC-AUTH-10] Código de 2FA ({}) enviado a: {}", action, email);
    }

    /** Verifica el código de activar/desactivar; si es correcto queda consumido. */
    public void verifySettingsCode(String email, TwoFactorAction action, String code) {
        verifyCode(settingsKey(email, action), "2fa:" + action + ":" + email, code, email);
    }

    /** Abre el reto del inicio de sesión: envía el código y devuelve el challengeId. */
    public String startLoginChallenge(String email) {
        String challengeId = UUID.randomUUID().toString();
        issueCode(loginCodeKey(challengeId), email, LOGIN_CODE_MINUTES);
        tokenRedisRepository.saveRefreshToken(challengeKey(challengeId), email, minutesToMillis(LOGIN_CODE_MINUTES));
        log.info("[AUDIT] [SEC-AUTH-10] Reto de 2FA abierto para: {}", email);
        return challengeId;
    }

    /** Verifica el código del reto y devuelve el correo de la cuenta que lo abrió. */
    public String verifyLoginChallenge(String challengeId, String code) {
        String email = tokenRedisRepository.getRefreshToken(challengeKey(challengeId));
        if (email == null) {
            throw new IllegalArgumentException("Código inválido o expirado");
        }
        try {
            verifyCode(loginCodeKey(challengeId), "2fa:login:" + challengeId, code, email);
        } catch (TooManyAttemptsException e) {
            // Sin código no hay cómo resolver el reto: hay que volver a iniciar sesión.
            tokenRedisRepository.deleteRefreshToken(challengeKey(challengeId));
            throw e;
        }
        tokenRedisRepository.deleteRefreshToken(challengeKey(challengeId));
        return email;
    }

    /** Primero se envía y luego se guarda: si el correo falla no queda un código que nadie recibió. */
    private void issueCode(String codeKey, String email, int minutes) {
        String code = otpGenerator.generate();
        resendEmailService.sendTwoFactorCodeEmail(email, code, minutes);
        tokenRedisRepository.saveRefreshToken(codeKey, code, minutesToMillis(minutes));
    }

    private void verifyCode(String codeKey, String attemptKey, String submitted, String email) {
        String stored = tokenRedisRepository.getRefreshToken(codeKey);
        boolean valid = stored != null && submitted != null && MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8), submitted.getBytes(StandardCharsets.UTF_8));

        if (!valid) {
            log.warn("[SECURITY] [SEC-AUTH-04] Código de 2FA incorrecto o vencido para: {}", email);
            if (otpAttemptService.registerFailureAndCheckLimit(attemptKey)) {
                tokenRedisRepository.deleteRefreshToken(codeKey);
                log.warn("[SECURITY] [SEC-AUTH-06] Código de 2FA invalidado por exceso de intentos para: {}", email);
                throw new TooManyAttemptsException("Demasiados intentos fallidos. Solicita un nuevo código.");
            }
            throw new IllegalArgumentException("Código inválido o expirado");
        }

        tokenRedisRepository.deleteRefreshToken(codeKey);
        otpAttemptService.reset(attemptKey);
    }

    private static long minutesToMillis(int minutes) {
        return minutes * 60_000L;
    }

    private static String settingsKey(String email, TwoFactorAction action) {
        return "2fa_code:" + action + ":" + email;
    }

    private static String loginCodeKey(String challengeId) {
        return "2fa_code:LOGIN:" + challengeId;
    }

    private static String challengeKey(String challengeId) {
        return "2fa_challenge:" + challengeId;
    }
}
