package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Vista pública del perfil. Nunca incluye datos sensibles como la URL del documento de identidad.
 */
@Data
@Builder
public class ProfileResponseDTO {
    private String userId;
    private String fullName;
    private String description;
    private String photoUrl;
    private List<String> categories;
    private String locationCoverage;
    private Float reputationScore;
    private int totalReviews;
    private VerificationStatus identityVerificationStatus;
    /** RF-AUTHPR-8: tareas completadas como prestador. */
    private int tasksCompleted;
    /** RF-AUTHPR-8: desde cuándo es miembro (alta del perfil); el front calcula la antigüedad a partir de aquí. */
    private Instant memberSince;

    public static ProfileResponseDTO fromProfile(Profile profile) {
        return ProfileResponseDTO.builder()
                .userId(profile.getUserId())
                .fullName(profile.getFullName())
                .description(profile.getDescription())
                .photoUrl(profile.getPhotoUrl())
                .categories(profile.getCategories())
                .locationCoverage(profile.getLocationCoverage())
                .reputationScore(profile.getReputationScore())
                .totalReviews(profile.getTotalReviews())
                .identityVerificationStatus(profile.getIdentityVerificationStatus())
                .tasksCompleted(profile.getTasksCompleted())
                .memberSince(profile.getCreatedAt() == null ? null
                        : profile.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .build();
    }
}
