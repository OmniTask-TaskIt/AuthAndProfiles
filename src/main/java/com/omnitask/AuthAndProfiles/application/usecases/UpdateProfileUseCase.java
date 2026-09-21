package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProfileUseCase {

    private final ProfileRepository profileRepository;

    public Profile updateProfile(String userId, String description, String photoUrl, String locationCoverage,
            List<String> categories) {
        Profile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> Profile.builder().userId(userId).identityVerificationStatus(VerificationStatus.UNVERIFIED)
                        .createdAt(LocalDateTime.now()).build());

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
}
