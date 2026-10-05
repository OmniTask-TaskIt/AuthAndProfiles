package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.PasswordResetAttemptService;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.exceptions.TooManyAttemptsException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ResetPasswordRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * RF-AUTH-4 (paso 2) y RF-AUTH-14: valida el código recibido por correo, cambia la contraseña,
 * cierra las sesiones abiertas (el refresh token deja de servir) y avisa al usuario por correo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRedisRepository tokenRedisRepository;
    private final PasswordResetAttemptService passwordResetAttemptService;
    private final ResendEmailService resendEmailService;
    private final EventPublisher eventPublisher;

    public void execute(ResetPasswordRequestDTO request, String clientIp) {
        String email = request.getEmail();
        String codeKey = ForgotPasswordUseCase.KEY_PREFIX + email;
        String storedCode = tokenRedisRepository.getRefreshToken(codeKey);

        if (storedCode == null || !storedCode.equals(request.getCode())) {
            log.warn("[SECURITY] [SEC-AUTH-04] Intento fallido de restablecer contraseña para el correo: {}", email);

            if (passwordResetAttemptService.registerFailureAndCheckLimit(email)) {
                // Se alcanzó el máximo de intentos: el código actual se invalida y hay que pedir uno nuevo.
                tokenRedisRepository.deleteRefreshToken(codeKey);
                log.warn("[SECURITY] [SEC-AUTH-06] Código de recuperación invalidado por exceso de intentos: {}", email);
                throw new TooManyAttemptsException(
                        "Demasiados intentos fallidos. Solicita un nuevo código de recuperación.");
            }
            throw new IllegalArgumentException("Código inválido o expirado");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // El código es de un solo uso y las sesiones abiertas dejan de poder renovarse.
        tokenRedisRepository.deleteRefreshToken(codeKey);
        tokenRedisRepository.deleteRefreshToken(user.getEmail());
        passwordResetAttemptService.reset(email);

        eventPublisher.publish(EventType.SECURITY_AUDIT, user.getEmail(),
                new SecurityAuditEvent("PASSWORD_RESET_COMPLETED", user.getEmail(), null, clientIp, Instant.now()));
        log.info("[AUDIT] [SEC-AUTH-05] Contraseña restablecida para: {}", user.getEmail());

        notifyPasswordChanged(user.getEmail());
    }

    /** El aviso es best-effort: si Resend falla, la contraseña ya cambió y no se revierte. */
    private void notifyPasswordChanged(String email) {
        try {
            resendEmailService.sendPasswordChangedEmail(email);
        } catch (RuntimeException e) {
            log.warn("[AUDIT] No se pudo enviar el aviso de cambio de contraseña a {}: {}", email, e.getMessage());
        }
    }
}