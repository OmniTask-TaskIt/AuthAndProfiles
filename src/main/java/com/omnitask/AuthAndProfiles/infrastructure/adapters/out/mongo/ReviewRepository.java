package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo;

import com.omnitask.AuthAndProfiles.domain.models.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ReviewRepository extends MongoRepository<Review, String> {

    boolean existsByTaskIdAndReviewerId(String taskId, String reviewerId);

    Page<Review> findByRevieweeIdOrderByCreatedAtDesc(String revieweeId, Pageable pageable);

    /** Usado por ReputationService para recalcular el promedio; el volumen por usuario es acotado. */
    List<Review> findByRevieweeId(String revieweeId);
}
