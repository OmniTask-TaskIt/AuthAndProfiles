package com.omnitask.AuthAndProfiles.application.usecases;

import java.time.Instant;
import com.omnitask.AuthAndProfiles.domain.events.AccountStatusChangedEvent;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.AccessRevocationRepository;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Suspende, bloquea o reactiva una cuenta (acción de administrador).
 * Al restringir una cuenta se revocan sus tokens: el refresh token se elimina de Redis y
 * los access token ya emitidos se rechazan en el filtro JWT hasta que expiren.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeAccountStatusUseCase {

    private final UserRepository userRepository;
    private final TokenRedisRepository tokenRedisRepository;
    private final AccessRevocationRepository accessRevocationRepository;
    private final JwtService jwtService;
    private final EventPublisher eventPublisher;

    public User execute(String userId, AccountStatus newStatus, String reason, String actorEmail) {
        if (newStatus == AccountStatus.PENDING_VERIFICATION) {
            throw new IllegalArgumentException("Solo se puede establecer ACTIVE, SUSPENDED o BLOCKED.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("No se puede modificar el estado de una cuenta de administrador.");
        }

        AccountStatus previousStatus = user.getAccountStatus();

        if (newStatus.isRestricted()) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Debes indicar el motivo de la suspensión o el bloqueo.");
            }
            user.setAccountStatus(newStatus);
            user.setBlockReason(reason.trim());

            tokenRedisRepository.deleteRefreshToken(user.getEmail());
            accessRevocationRepository.revokeUser(user.getEmail(), jwtService.getAccessTokenExpirationMillis());
        } else {
            // Reactivar nunca salta la verificación de correo.
            user.setAccountStatus(user.isEmailVerified() ? AccountStatus.ACTIVE : AccountStatus.PENDING_VERIFICATION);
            user.setBlockReason(null);

            accessRevocationRepository.clearUserRevocation(user.getEmail());
        }

        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        eventPublisher.publish(EventType.ACCOUNT_STATUS_CHANGED, saved.getId(),
                new AccountStatusChangedEvent(saved.getId(), saved.getEmail(),
                        previousStatus == null ? null : previousStatus.name(), saved.getAccountStatus().name(),
                        saved.getBlockReason(), actorEmail, Instant.now()));

        log.warn("[AUDIT-SECURITY] Estado de cuenta de {} cambiado a {} por el administrador {}. Motivo: {}",
                saved.getEmail(), saved.getAccountStatus(), actorEmail, reason);
        return saved;
    }
}
