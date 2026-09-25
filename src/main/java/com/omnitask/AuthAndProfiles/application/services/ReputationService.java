package com.omnitask.AuthAndProfiles.application.services;

import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.ReputationUpdatedEvent;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.Review;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Recalcula el promedio de reputación de un perfil a partir de TODAS sus reseñas (RF-AUTHPR-6). Se recalcula
 * completo en lugar de incrementalmente para no arrastrar errores de redondeo ni depender de que nunca se
 * borre una reseña. reputationScore y totalReviews nunca se editan manualmente (PN-AUTHPR-11): esta es la
 * única vía que los modifica.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReputationService {

    private final ProfileRepository profileRepository;
    private final ReviewRepository reviewRepository;
    private final EventPublisher eventPublisher;

    public void recalculate(String userId) {
        List<Review> reviews = reviewRepository.findByRevieweeId(userId);
        if (reviews.isEmpty()) {
            return;
        }

        double average = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        float rounded = Math.round(average * 100f) / 100f;

        profileRepository.findByUserId(userId).ifPresentOrElse(profile -> {
            profile.setReputationScore(rounded);
            profile.setTotalReviews(reviews.size());
            profile.setUpdatedAt(LocalDateTime.now());
            profileRepository.save(profile);

            eventPublisher.publish(EventType.REPUTATION_UPDATED, userId,
                    new ReputationUpdatedEvent(userId, rounded, reviews.size(), Instant.now()));

            log.info("[AUDIT] Reputación recalculada para el usuario {}: {} ({} reseñas)", userId, rounded,
                    reviews.size());
        }, () -> log.warn("[AUDIT] No se recalculó la reputación: no existe perfil para el usuario {}", userId));
    }
}
