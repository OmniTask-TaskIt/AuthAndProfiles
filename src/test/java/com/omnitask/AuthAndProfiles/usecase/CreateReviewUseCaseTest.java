package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.ReputationService;
import com.omnitask.AuthAndProfiles.application.usecases.CreateReviewUseCase;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.ReviewCreatedEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.ConflictException;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.Review;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReviewRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateReviewUseCaseTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReputationService reputationService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private CreateReviewUseCase createReviewUseCase;

    private User reviewer;

    @BeforeEach
    void setUp() {
        reviewer = User.builder().id("user-1").email("test@gmail.com").build();
    }

    @Test
    void execute_deberiaGuardarLaResenaRecalcularReputacionYPublicarElEvento() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(reviewer));
        when(profileRepository.findByUserId("user-2")).thenReturn(Optional.of(Profile.builder().userId("user-2").build()));
        when(reviewRepository.existsByTaskIdAndReviewerId("task-1", "user-1")).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId("review-1");
            return r;
        });

        Review result = createReviewUseCase.execute("test@gmail.com", "task-1", "user-2", 5, "Excelente");

        assertThat(result.getId()).isEqualTo("review-1");
        assertThat(result.getReviewerId()).isEqualTo("user-1");
        assertThat(result.getRevieweeId()).isEqualTo("user-2");
        assertThat(result.getRating()).isEqualTo(5);
        assertThat(result.getCreatedAt()).isNotNull();
        verify(reputationService).recalculate("user-2");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq(EventType.REVIEW_CREATED), eq("user-2"), payload.capture());
        ReviewCreatedEvent event = (ReviewCreatedEvent) payload.getValue();
        assertThat(event.reviewId()).isEqualTo("review-1");
        assertThat(event.rating()).isEqualTo(5);
    }

    @Test
    void execute_deberiaRechazarAutoCalificacion() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(reviewer));

        assertThatThrownBy(() -> createReviewUseCase.execute("test@gmail.com", "task-1", "user-1", 5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a ti mismo");
        verifyNoInteractions(reviewRepository, reputationService, eventPublisher);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioCalificadoNoExiste() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(reviewer));
        when(profileRepository.findByUserId("user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createReviewUseCase.execute("test@gmail.com", "task-1", "user-2", 5, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("no existe");
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void execute_deberiaRechazarUnaSegundaCalificacionParaLaMismaTarea() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(reviewer));
        when(profileRepository.findByUserId("user-2")).thenReturn(Optional.of(Profile.builder().userId("user-2").build()));
        when(reviewRepository.existsByTaskIdAndReviewerId("task-1", "user-1")).thenReturn(true);

        assertThatThrownBy(() -> createReviewUseCase.execute("test@gmail.com", "task-1", "user-2", 5, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya calificaste");
        verify(reviewRepository, never()).save(any());
        verifyNoInteractions(reputationService, eventPublisher);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElReviewerNoExiste() {
        when(userRepository.findByEmail("fantasma@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createReviewUseCase.execute("fantasma@gmail.com", "task-1", "user-2", 5, null))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(reviewRepository, reputationService, eventPublisher);
    }
}
