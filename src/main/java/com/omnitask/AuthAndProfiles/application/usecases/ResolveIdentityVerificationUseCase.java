package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.enums.VerificationDecision;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.IdentityVerificationUpdatedEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Aplica el resultado de la revisión humana (HITL) del documento de identidad. Lo usan el consumidor de
 * Kafka y el endpoint manual de administración. Es idempotente: repetir la misma decisión no hace nada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveIdentityVerificationUseCase {

    private final ProfileRepository profileRepository;
    private final EventPublisher eventPublisher;

    public Profile execute(String userId, VerificationDecision decision, String reason, String reviewedBy) {
        Profile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Perfil no encontrado para este usuario"));

        VerificationStatus target = decision == VerificationDecision.APPROVED
                ? VerificationStatus.VERIFIED
                : VerificationStatus.REJECTED;

        if (profile.getIdentityVerificationStatus() == target) {
            log.info("[AUDIT] La verificación del usuario {} ya estaba en {}, no hay cambios.", userId, target);
            return profile;
        }
        if (profile.getIdentityVerificationStatus() != VerificationStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException("El usuario no tiene un documento pendiente de revisión.");
        }
        if (target == VerificationStatus.REJECTED && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Debes indicar el motivo del rechazo.");
        }

        profile.setIdentityVerificationStatus(target);
        profile.setVerificationReason(target == VerificationStatus.REJECTED ? reason.trim() : null);
        profile.setVerificationReviewedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        Profile saved = profileRepository.save(profile);

        eventPublisher.publish(EventType.IDENTITY_VERIFICATION_UPDATED, userId,
                new IdentityVerificationUpdatedEvent(userId, target.name(), saved.getVerificationReason(),
                        reviewedBy, Instant.now()));

        log.info("[AUDIT] [SEC-AUTH-04] Verificación de identidad del usuario {} resuelta como {} por {}", userId,
                target, reviewedBy);
        return saved;
    }
}
