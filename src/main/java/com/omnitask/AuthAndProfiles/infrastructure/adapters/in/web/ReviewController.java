package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web;

import com.omnitask.AuthAndProfiles.application.usecases.CreateReviewUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.GetUserReviewsUseCase;
import com.omnitask.AuthAndProfiles.domain.models.Review;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.CreateReviewRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ReviewResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Calificaciones y reseñas entre usuarios (RF-AUTHPR-5 y RF-AUTHPR-6). */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final CreateReviewUseCase createReviewUseCase;
    private final GetUserReviewsUseCase getUserReviewsUseCase;

    @PostMapping
    public ResponseEntity<ReviewResponseDTO> createReview(
            @Valid @RequestBody CreateReviewRequestDTO request,
            Authentication authentication) {
        Review review = createReviewUseCase.execute(authentication.getName(), request.getTaskId(),
                request.getRevieweeId(), request.getRating(), request.getComment());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponseDTO.fromReview(review));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<PageResponseDTO<ReviewResponseDTO>> getUserReviews(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(getUserReviewsUseCase.execute(userId, page, size));
    }
}
