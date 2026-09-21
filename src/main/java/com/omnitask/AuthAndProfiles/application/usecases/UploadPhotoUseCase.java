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

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadPhotoUseCase {

    private final ProfileRepository profileRepository;
    private final AzureBlobService azureBlobService;

    public String execute(String userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen está vacío.");
        }
        String photoUrl = azureBlobService.uploadFile(file);

        Profile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> Profile.builder().userId(userId).identityVerificationStatus(VerificationStatus.UNVERIFIED)
                        .createdAt(LocalDateTime.now()).build());

        profile.setPhotoUrl(photoUrl);
        profile.setUpdatedAt(LocalDateTime.now());
        profileRepository.save(profile);

        log.info("[AUDIT] [SEC-AUTH-04] Foto de perfil subida a Azure y actualizada con éxito para el usuario ID: {}",
                userId);
        return photoUrl;
    }
}
