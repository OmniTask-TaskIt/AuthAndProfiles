package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class CreateProfileUseCase {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

    public Profile execute(String userId) {
        return profileRepository.findByUserId(userId).orElseGet(() -> {

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("Usuario no encontrado para crear perfil"));

            Profile newProfile = Profile.builder()
                    .userId(userId)
                    .fullName(user.getName())
                    .currentRole(user.getRole() != null ? user.getRole().name() : "UNASSIGNED")
                    .reputationScore(5.0f)
                    .totalReviews(0)
                    .description("")
                    .locationCoverage("")
                    .categories(new ArrayList<>())
                    .identityVerificationStatus(VerificationStatus.UNVERIFIED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            return profileRepository.save(newProfile);
        });
    }
}