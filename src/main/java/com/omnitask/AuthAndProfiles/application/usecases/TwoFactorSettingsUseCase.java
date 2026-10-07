package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.TwoFactorService;
import com.omnitask.AuthAndProfiles.domain.enums.TwoFactorAction;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * RF-AUTH-9: activar y desactivar el segundo factor desde la cuenta. Ambos cambios se hacen en dos pasos: se pide un
 * código (llega al correo) y se confirma con él. Desactivarlo también exige código para que una sesión robada no
 * pueda quitar la protección.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorSettingsUseCase {

    private final UserRepository userRepository;
    private final TwoFactorService twoFactorService;
    private final EventPublisher eventPublisher;

    public boolean isEnabled(String email) {
        return findUser(email).isTwoFactorEnabled();
    }

    /** Paso 1: envía el código al correo. */
    public void requestCode(String email, TwoFactorAction action) {
        ensureChangeApplies(findUser(email), action);
        twoFactorService.sendSettingsCode(email, action);
    }

    /** Paso 2: con el código correcto aplica el cambio. */
    public void confirm(String email, String code, TwoFactorAction action) {
        User user = findUser(email);
        ensureChangeApplies(user, action);

        twoFactorService.verifySettingsCode(email, action, code);

        user.setTwoFactorEnabled(action == TwoFactorAction.ENABLE);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        String auditAction = action == TwoFactorAction.ENABLE ? "2FA_ENABLED" : "2FA_DISABLED";
        eventPublisher.publish(EventType.SECURITY_AUDIT, email,
                new SecurityAuditEvent(auditAction, email, null, null, Instant.now()));
        log.info("[AUDIT] [SEC-AUTH-10] Verificación en dos pasos {} para: {}",
                action == TwoFactorAction.ENABLE ? "activada" : "desactivada", email);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private void ensureChangeApplies(User user, TwoFactorAction action) {
        if (action == TwoFactorAction.ENABLE && user.isTwoFactorEnabled()) {
            throw new IllegalArgumentException("La verificación en dos pasos ya está activada.");
        }
        if (action == TwoFactorAction.DISABLE && !user.isTwoFactorEnabled()) {
            throw new IllegalArgumentException("La verificación en dos pasos no está activada.");
        }
    }
}
