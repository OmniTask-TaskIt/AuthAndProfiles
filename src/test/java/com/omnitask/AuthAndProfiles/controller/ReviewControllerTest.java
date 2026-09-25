package com.omnitask.AuthAndProfiles.controller;

import com.omnitask.AuthAndProfiles.application.usecases.CreateReviewUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.GetUserReviewsUseCase;
import com.omnitask.AuthAndProfiles.domain.models.Review;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.ReviewController;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.CreateReviewRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ReviewResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock
    private CreateReviewUseCase createReviewUseCase;
    @Mock
    private GetUserReviewsUseCase getUserReviewsUseCase;

    @InjectMocks
    private ReviewController reviewController;

    @Test
    void createReview_deberiaUsarElEmailAutenticadoYRetornar201() {
        CreateReviewRequestDTO request = new CreateReviewRequestDTO();
        request.setTaskId("task-1");
        request.setRevieweeId("user-2");
        request.setRating(5);
        request.setComment("Excelente");
        Authentication auth = new UsernamePasswordAuthenticationToken("test@gmail.com", null, List.of());
        Review saved = Review.builder().id("review-1").taskId("task-1").reviewerId("user-1").revieweeId("user-2")
                .rating(5).comment("Excelente").createdAt(LocalDateTime.now()).build();
        when(createReviewUseCase.execute("test@gmail.com", "task-1", "user-2", 5, "Excelente")).thenReturn(saved);

        ResponseEntity<ReviewResponseDTO> response = reviewController.createReview(request, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isEqualTo("review-1");
        assertThat(response.getBody().rating()).isEqualTo(5);
    }

    @Test
    void getUserReviews_deberiaDelegarConLaPaginacionSolicitada() {
        PageResponseDTO<ReviewResponseDTO> page = new PageResponseDTO<>(List.of(), 1, 10, 0, 0);
        when(getUserReviewsUseCase.execute("user-2", 1, 10)).thenReturn(page);

        ResponseEntity<PageResponseDTO<ReviewResponseDTO>> response = reviewController.getUserReviews("user-2", 1,
                10);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(page);
    }
}
