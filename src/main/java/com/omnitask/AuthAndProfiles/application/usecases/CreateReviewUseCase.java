package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.ReputationService;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.ReviewCreatedEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.ConflictException;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Review;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReviewRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Registra la calificación que un usuario deja a otro por una tarea (RF-AUTHPR-5). Un mismo usuario solo
 * puede calificar la misma tarea una vez (RNF-AUTHPR-14).
 * <p>
 * Nota: por ahora no se valida contra Task Service que la tarea esté completada ni que el reviewer haya
 * participado en ella (PN-AUTHPR-8), porque ese microservicio aún no existe. Cuando esté disponible, esta
 * clase debe consultarlo (síncrono o vía el evento que él publique) antes de aceptar la reseña.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateReviewUseCase {

    private final ReviewRepository reviewRepository;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ReputationService reputationService;
    private final EventPublisher eventPublisher;

    public Review execute(String reviewerEmail, String taskId, String revieweeId, int rating, String comment) {
        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        if (reviewer.getId().equals(revieweeId)) {
            throw new IllegalArgumentException("No puedes calificarte a ti mismo.");
        }
        if (profileRepository.findByUserId(revieweeId).isEmpty()) {
            throw new NotFoundException("El usuario calificado no existe.");
        }
        if (reviewRepository.existsByTaskIdAndReviewerId(taskId, reviewer.getId())) {
            throw new ConflictException("Ya calificaste esta tarea.");
        }

        Review review = reviewRepository.save(Review.builder()
                .taskId(taskId)
                .reviewerId(reviewer.getId())
                .revieweeId(revieweeId)
                .rating(rating)
                .comment(comment)
                .createdAt(LocalDateTime.now())
                .build());

        eventPublisher.publish(EventType.REVIEW_CREATED, revieweeId,
                new ReviewCreatedEvent(review.getId(), taskId, reviewer.getId(), revieweeId, rating, Instant.now()));

        reputationService.recalculate(revieweeId);

        log.info("[AUDIT] Reseña {} registrada: {} calificó a {} en la tarea {} con {} estrellas", review.getId(),
                reviewer.getId(), revieweeId, taskId, rating);
        return review;
    }
}
