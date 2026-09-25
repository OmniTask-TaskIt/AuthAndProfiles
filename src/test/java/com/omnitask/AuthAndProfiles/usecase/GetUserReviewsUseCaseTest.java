package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.GetUserReviewsUseCase;
import com.omnitask.AuthAndProfiles.domain.models.Review;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ReviewResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserReviewsUseCaseTest {

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private GetUserReviewsUseCase getUserReviewsUseCase;

    @Test
    void execute_deberiaMapearLasResenasAlDtoDeRespuesta() {
        Review review = Review.builder().id("review-1").taskId("task-1").reviewerId("user-1")
                .revieweeId("user-2").rating(5).comment("Genial").createdAt(LocalDateTime.now()).build();
        Page<Review> page = new PageImpl<>(List.of(review));
        when(reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(eq("user-2"), any(Pageable.class)))
                .thenReturn(page);

        PageResponseDTO<ReviewResponseDTO> result = getUserReviewsUseCase.execute("user-2", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo("review-1");
        assertThat(result.content().get(0).rating()).isEqualTo(5);
    }

    @Test
    void execute_deberiaLimitarElTamanoDePaginaYCorregirValoresInvalidos() {
        when(reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(eq("user-2"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        getUserReviewsUseCase.execute("user-2", -3, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findByRevieweeIdOrderByCreatedAtDesc(eq("user-2"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }
}
