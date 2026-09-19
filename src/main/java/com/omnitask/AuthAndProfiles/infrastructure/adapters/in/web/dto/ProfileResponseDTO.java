package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import lombok.Builder;
import lombok.Data;
import java.util.List;

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
    private VerificationStatus institutionalVerificationStatus;
}
