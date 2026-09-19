package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.AzureBlobService;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProfileUseCase {

    private final ProfileRepository profileRepository;
    private final AzureBlobService azureBlobService;

    public Profile updateProfile(String userId, String description, String photoUrl, String locationCoverage,
            List<String> categories) {
        Profile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> Profile.builder().userId(userId).createdAt(LocalDateTime.now()).build());

        if (description != null)
            profile.setDescription(description);
        if (photoUrl != null)
            profile.setPhotoUrl(photoUrl);
        if (locationCoverage != null)
            profile.setLocationCoverage(locationCoverage);
        if (categories != null)
            profile.setCategories(categories);

        profile.setUpdatedAt(LocalDateTime.now());
        return profileRepository.save(profile);
    }

    public Profile uploadDocument(String userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo del documento de identidad está vacío.");
        }

        String documentUrl = azureBlobService.uploadFile(file);

        Profile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Perfil no encontrado para este usuario"));

        profile.setDocumentUrl(documentUrl);
        profile.setIdentityVerificationStatus(VerificationStatus.PENDING_REVIEW);
        profile.setUpdatedAt(LocalDateTime.now());

        log.info(
                "[AUDIT] [SEC-AUTH-04] Documento de identidad subido a Azure y enviado a revisión HITL para el usuario ID: {}",
                userId);

        return profileRepository.save(profile);
    }
}
