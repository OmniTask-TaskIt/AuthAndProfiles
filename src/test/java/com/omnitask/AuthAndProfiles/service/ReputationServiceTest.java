package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.ReputationService;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.ReputationUpdatedEvent;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.Review;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReputationServiceTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ReputationService reputationService;

    private Review review(int rating) {
        return Review.builder().revieweeId("user-1").rating(rating).build();
    }

    @Test
    void recalculate_deberiaPromediarLasCalificacionesYActualizarElPerfil() {
        when(reviewRepository.findByRevieweeId("user-1")).thenReturn(List.of(review(5), review(4), review(3)));
        Profile profile = Profile.builder().userId("user-1").reputationScore(5.0f).totalReviews(0).build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));

        reputationService.recalculate("user-1");

        assertThat(profile.getReputationScore()).isEqualTo(4.0f);
        assertThat(profile.getTotalReviews()).isEqualTo(3);
        assertThat(profile.getUpdatedAt()).isNotNull();
        verify(profileRepository).save(profile);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq(EventType.REPUTATION_UPDATED), eq("user-1"), payload.capture());
        ReputationUpdatedEvent event = (ReputationUpdatedEvent) payload.getValue();
        assertThat(event.reputationScore()).isEqualTo(4.0f);
        assertThat(event.totalReviews()).isEqualTo(3);
    }

    @Test
    void recalculate_deberiaRedondearAdosDecimales() {
        when(reviewRepository.findByRevieweeId("user-1")).thenReturn(List.of(review(5), review(4), review(4)));
        Profile profile = Profile.builder().userId("user-1").build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));

        reputationService.recalculate("user-1");

        assertThat(profile.getReputationScore()).isEqualTo(4.33f);
    }

    @Test
    void recalculate_noDeberiaHacerNada_cuandoNoHayReseñas() {
        when(reviewRepository.findByRevieweeId("user-1")).thenReturn(List.of());

        reputationService.recalculate("user-1");

        verifyNoInteractions(profileRepository, eventPublisher);
    }

    @Test
    void recalculate_noDeberiaFallarNiPublicar_cuandoElPerfilNoExiste() {
        when(reviewRepository.findByRevieweeId("user-1")).thenReturn(List.of(review(5)));
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        reputationService.recalculate("user-1");

        verify(profileRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
