package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web;

import com.omnitask.AuthAndProfiles.application.usecases.DeleteAccountUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.GetProfileUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.SearchProfileUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.SwitchRoleUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.UpdateProfileUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.UploadPhotoUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ProfileResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final GetProfileUseCase getProfileUseCase;
    private final UploadPhotoUseCase uploadPhotoUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final SwitchRoleUseCase switchRoleUseCase;
    private final DeleteAccountUseCase deleteAccountUseCase;
    private final SearchProfileUseCase searchProfileUseCase;

    @GetMapping("/{userId}")
    public ResponseEntity<ProfileResponseDTO> getProfile(@PathVariable String userId) {
        Profile profile = getProfileUseCase.execute(userId);

        ProfileResponseDTO response = ProfileResponseDTO.builder()
                .userId(profile.getUserId())
                .description(profile.getDescription())
                .photoUrl(profile.getPhotoUrl())
                .categories(profile.getCategories())
                .locationCoverage(profile.getLocationCoverage())
                .reputationScore(profile.getReputationScore())
                .totalReviews(profile.getTotalReviews())
                .identityVerificationStatus(profile.getIdentityVerificationStatus())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/photo")
    public ResponseEntity<String> uploadPhoto(@PathVariable String userId, @RequestParam("file") MultipartFile file) {
        String photoUrl = uploadPhotoUseCase.execute(userId, file);
        return ResponseEntity.ok(photoUrl);
    }

    @PostMapping("/{userId}/document")
    public ResponseEntity<Profile> uploadDocument(@PathVariable String userId,
            @RequestParam("file") MultipartFile file) {
        Profile updatedProfile = updateProfileUseCase.uploadDocument(userId, file);
        return ResponseEntity.ok(updatedProfile);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<Profile> updateProfile(
            @PathVariable String userId,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String photoUrl,
            @RequestParam(required = false) String locationCoverage,
            @RequestParam(required = false) List<String> categories) {

        Profile updatedProfile = updateProfileUseCase.updateProfile(userId, description, photoUrl, locationCoverage,
                categories);
        return ResponseEntity.ok(updatedProfile);
    }

    @PostMapping("/switch-role")
    public ResponseEntity<AuthResponseDTO> switchRole(
            @RequestParam String email,
            @RequestParam Role newRole) {

        AuthResponseDTO response = switchRoleUseCase.execute(email, newRole);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Map<String, String>> deleteAccount(@PathVariable String email) {
        deleteAccountUseCase.execute(email);
        return ResponseEntity.ok(Map.of(
                "message", "Cuenta eliminada permanentemente para el correo: " + email));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProfileResponseDTO>> searchProfiles(@RequestParam String name) {
        List<Profile> profiles = searchProfileUseCase.execute(name);

        List<ProfileResponseDTO> responseList = profiles.stream().map(profile -> ProfileResponseDTO.builder()
                .userId(profile.getUserId())
                .fullName(profile.getFullName())
                .description(profile.getDescription())
                .photoUrl(profile.getPhotoUrl())
                .categories(profile.getCategories())
                .locationCoverage(profile.getLocationCoverage())
                .reputationScore(profile.getReputationScore())
                .totalReviews(profile.getTotalReviews())
                .identityVerificationStatus(profile.getIdentityVerificationStatus())
                .build()).collect(Collectors.toList());

        return ResponseEntity.ok(responseList);
    }

}
