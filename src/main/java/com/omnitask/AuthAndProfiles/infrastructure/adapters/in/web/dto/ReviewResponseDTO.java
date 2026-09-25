package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import com.omnitask.AuthAndProfiles.domain.models.Review;

import java.time.LocalDateTime;

public record ReviewResponseDTO(
        String id,
        String taskId,
        String reviewerId,
        String revieweeId,
        int rating,
        String comment,
        LocalDateTime createdAt) {

    public static ReviewResponseDTO fromReview(Review review) {
        return new ReviewResponseDTO(review.getId(), review.getTaskId(), review.getReviewerId(),
                review.getRevieweeId(), review.getRating(), review.getComment(), review.getCreatedAt());
    }
}
