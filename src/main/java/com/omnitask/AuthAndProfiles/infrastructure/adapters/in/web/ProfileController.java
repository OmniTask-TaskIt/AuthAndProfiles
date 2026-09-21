package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web;

import com.omnitask.AuthAndProfiles.domain.enums.DocumentType;
import com.omnitask.AuthAndProfiles.application.usecases.SubmitIdentityDocumentUseCase;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final SubmitIdentityDocumentUseCase submitIdentityDocumentUseCase;
    private final SwitchRoleUseCase switchRoleUseCase;
    private final DeleteAccountUseCase deleteAccountUseCase;
    private final SearchProfileUseCase searchProfileUseCase;

    @GetMapping("/{userId}")
    public ResponseEntity<ProfileResponseDTO> getProfile(@PathVariable String userId) {
        Profile profile = getProfileUseCase.execute(userId);
        return ResponseEntity.ok(ProfileResponseDTO.fromProfile(profile));
    }

    @PreAuthorize("@profileSecurity.isOwner(#userId, authentication)")
    @PostMapping("/{userId}/photo")
    public ResponseEntity<String> uploadPhoto(@PathVariable String userId, @RequestParam("file") MultipartFile file) {
        String photoUrl = uploadPhotoUseCase.execute(userId, file);
        return ResponseEntity.ok(photoUrl);
    }

    @PreAuthorize("@profileSecurity.isOwner(#userId, authentication)")
    @PostMapping("/{userId}/document")
    public ResponseEntity<ProfileResponseDTO> uploadDocument(@PathVariable String userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", defaultValue = "CEDULA") DocumentType documentType) {
        Profile updatedProfile = submitIdentityDocumentUseCase.execute(userId, documentType, file);
        return ResponseEntity.ok(ProfileResponseDTO.fromProfile(updatedProfile));
    }

    @PreAuthorize("@profileSecurity.isOwner(#userId, authentication)")
    @PatchMapping("/{userId}")
    public ResponseEntity<ProfileResponseDTO> updateProfile(
            @PathVariable String userId,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String photoUrl,
            @RequestParam(required = false) String locationCoverage,
            @RequestParam(required = false) List<String> categories) {

        Profile updatedProfile = updateProfileUseCase.updateProfile(userId, description, photoUrl, locationCoverage,
                categories);
        return ResponseEntity.ok(ProfileResponseDTO.fromProfile(updatedProfile));
    }

    /** Un usuario solo puede cambiar su propio rol. */
    @PreAuthorize("#email == authentication.name")
    @PostMapping("/switch-role")
    public ResponseEntity<AuthResponseDTO> switchRole(
            @RequestParam String email,
            @RequestParam Role newRole) {

        AuthResponseDTO response = switchRoleUseCase.execute(email, newRole);
        return ResponseEntity.ok(response);
    }

    /** Un usuario puede eliminar su propia cuenta; un administrador puede eliminar cualquiera. */
    @PreAuthorize("#email == authentication.name or hasRole('ADMIN')")
    @DeleteMapping("/{email}")
    public ResponseEntity<Map<String, String>> deleteAccount(@PathVariable String email) {
        deleteAccountUseCase.execute(email);
        return ResponseEntity.ok(Map.of(
                "message", "Cuenta eliminada permanentemente para el correo: " + email));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProfileResponseDTO>> searchProfiles(@RequestParam String name) {
        List<ProfileResponseDTO> responseList = searchProfileUseCase.execute(name).stream()
                .map(ProfileResponseDTO::fromProfile)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseList);
    }

}
