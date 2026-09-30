package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ProfileResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Cola de verificaciones de identidad para el panel de administración.
 * Por defecto lista los perfiles en PENDING_REVIEW, ordenados por fecha de
 * envío del documento (el más antiguo primero), para que el equipo de
 * moderación los atienda en el orden en que llegaron.
 */
@Service
@RequiredArgsConstructor
public class ListPendingVerificationsUseCase {

    static final int MAX_PAGE_SIZE = 50;

    private final ProfileRepository profileRepository;

    public PageResponseDTO<ProfileResponseDTO> execute(VerificationStatus status, int page, int size) {
        VerificationStatus effectiveStatus = status == null ? VerificationStatus.PENDING_REVIEW : status;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "documentSubmittedAt"));
        return PageResponseDTO.from(
                profileRepository.findByIdentityVerificationStatus(effectiveStatus, pageable)
                        .map(ProfileResponseDTO::fromProfile));
    }
}
