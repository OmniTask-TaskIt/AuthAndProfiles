package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.OtpGenerator;
import com.omnitask.AuthAndProfiles.application.services.PasswordResetAttemptService;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * RF-AUTH-4 (paso 1): genera un código temporal de 6 dígitos y lo envía por correo con Resend.
 * <p>
 * Para no revelar qué correos están registrados, si la cuenta no existe, no tiene contraseña
 * (ingresó con Google/GitHub) o aún no verificó su correo, el caso de uso termina sin enviar nada
 * y sin error: la respuesta HTTP es la misma en todos los casos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForgotPasswordUseCase {

    /** Prefijo de la clave en Redis donde vive el código de recuperación de cada correo. */
    static final String KEY_PREFIX = "pwd_reset:";

    /** El código vence a los 15 minutos (PN-AUTHPR-4). */
    static final long CODE_TTL_MILLIS = 900_000L;

    private final UserRepository userRepository;
    private final TokenRedisRepository tokenRedisRepository;
    private final PasswordResetAttemptService passwordResetAttemptService;
    private final OtpGenerator otpGenerator;
    private final ResendEmailService resendEmailService;
    private final EventPublisher eventPublisher;

    public void execute(String email, String clientIp) {
        log.info("[AUDIT] [SEC-AUTH-05] Solicitud de recuperación de contraseña para: {}", email);

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || user.getAuthProvider() != AuthProvider.LOCAL || !user.isEmailVerified()) {
            log.warn("[SECURITY] Solicitud de recuperación ignorada (cuenta inexistente, sin contraseña local o sin verificar): {}",
                    email);
            return;
        }

        String code = otpGenerator.generate();
        String key = KEY_PREFIX + user.getEmail();

        // Un código nuevo invalida el anterior y reinicia el contador de intentos fallidos.
        tokenRedisRepository.saveRefreshToken(key, code, CODE_TTL_MILLIS);
        passwordResetAttemptService.reset(user.getEmail());

        try {
            resendEmailService.sendPasswordResetEmail(user.getEmail(), code);
        } catch (RuntimeException e) {
            // Nunca queda activo un código que el usuario no recibió.
            tokenRedisRepository.deleteRefreshToken(key);
            throw e;
        }

        eventPublisher.publish(EventType.SECURITY_AUDIT, user.getEmail(),
                new SecurityAuditEvent("PASSWORD_RESET_REQUESTED", user.getEmail(), null, clientIp, Instant.now()));
        log.info("[AUDIT] [SEC-AUTH-05] Código de recuperación enviado a: {}", user.getEmail());
    }
}