package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.models.Review;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ReviewResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Lista paginada de reseñas recibidas por un usuario, más recientes primero. */
@Service
@RequiredArgsConstructor
public class GetUserReviewsUseCase {

    static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository reviewRepository;

    public PageResponseDTO<ReviewResponseDTO> execute(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponseDTO.from(
                reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(userId, pageable)
                        .map(ReviewResponseDTO::fromReview));
    }
}
