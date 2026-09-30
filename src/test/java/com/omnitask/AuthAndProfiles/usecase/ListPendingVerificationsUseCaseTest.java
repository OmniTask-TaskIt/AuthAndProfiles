package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.ListPendingVerificationsUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ProfileResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListPendingVerificationsUseCaseTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ListPendingVerificationsUseCase listPendingVerificationsUseCase;

    @Test
    void execute_deberiaUsarPendingReviewComoEstadoPorDefecto() {
        when(profileRepository.findByIdentityVerificationStatus(eq(VerificationStatus.PENDING_REVIEW),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        listPendingVerificationsUseCase.execute(null, 0, 20);

        verify(profileRepository).findByIdentityVerificationStatus(eq(VerificationStatus.PENDING_REVIEW),
                any(Pageable.class));
    }

    @Test
    void execute_deberiaFiltrarPorElEstadoSolicitado() {
        when(profileRepository.findByIdentityVerificationStatus(eq(VerificationStatus.REJECTED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        listPendingVerificationsUseCase.execute(VerificationStatus.REJECTED, 0, 20);

        verify(profileRepository).findByIdentityVerificationStatus(eq(VerificationStatus.REJECTED), any(Pageable.class));
    }

    @Test
    void execute_deberiaOrdenarPorFechaDeEnvioDelDocumentoAscendente_paraAtenderAlMasAntiguoPrimero() {
        when(profileRepository.findByIdentityVerificationStatus(any(), any())).thenReturn(new PageImpl<>(List.of()));

        listPendingVerificationsUseCase.execute(VerificationStatus.PENDING_REVIEW, 0, 20);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(profileRepository).findByIdentityVerificationStatus(eq(VerificationStatus.PENDING_REVIEW),
                pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("documentSubmittedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void execute_deberiaMapearLosPerfilesAProfileResponseDTO() {
        Profile profile = Profile.builder().userId("user-1").fullName("Ana")
                .identityVerificationStatus(VerificationStatus.PENDING_REVIEW).build();
        when(profileRepository.findByIdentityVerificationStatus(any(), any()))
                .thenReturn(new PageImpl<>(List.of(profile)));

        PageResponseDTO<ProfileResponseDTO> result = listPendingVerificationsUseCase.execute(null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getUserId()).isEqualTo("user-1");
    }

    @Test
    void execute_deberiaLimitarElTamanoDePaginaAlMaximoPermitido() {
        when(profileRepository.findByIdentityVerificationStatus(any(), any())).thenReturn(new PageImpl<>(List.of()));

        listPendingVerificationsUseCase.execute(VerificationStatus.PENDING_REVIEW, 0, 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(profileRepository).findByIdentityVerificationStatus(eq(VerificationStatus.PENDING_REVIEW),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }
}
