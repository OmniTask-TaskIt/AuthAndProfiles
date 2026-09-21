package com.omnitask.AuthAndProfiles.controller;

import com.omnitask.AuthAndProfiles.domain.enums.DocumentType;
import com.omnitask.AuthAndProfiles.application.usecases.SubmitIdentityDocumentUseCase;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.ProfileController;

import com.omnitask.AuthAndProfiles.application.usecases.*;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ProfileResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private GetProfileUseCase getProfileUseCase;
    @Mock
    private UploadPhotoUseCase uploadPhotoUseCase;
    @Mock
    private UpdateProfileUseCase updateProfileUseCase;
    @Mock
    private SubmitIdentityDocumentUseCase submitIdentityDocumentUseCase;
    @Mock
    private SwitchRoleUseCase switchRoleUseCase;
    @Mock
    private DeleteAccountUseCase deleteAccountUseCase;
    @Mock
    private SearchProfileUseCase searchProfileUseCase;

    @InjectMocks
    private ProfileController profileController;

    @Test
    void getProfile_deberiaMapearElPerfilAlDto() {
        // Arrange
        Profile profile = Profile.builder()
                .userId("user-1")
                .description("desc")
                .photoUrl("url")
                .categories(List.of("plomería"))
                .locationCoverage("Bogotá")
                .reputationScore(4.5f)
                .totalReviews(3)
                .identityVerificationStatus(VerificationStatus.VERIFIED)
                .build();
        when(getProfileUseCase.execute("user-1")).thenReturn(profile);

        // Act
        ResponseEntity<ProfileResponseDTO> response = profileController.getProfile("user-1");

        // Assert
        ProfileResponseDTO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getUserId()).isEqualTo("user-1");
        assertThat(body.getReputationScore()).isEqualTo(4.5f);
        assertThat(body.getIdentityVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void uploadPhoto_deberiaDelegarEnElUseCaseYRetornarLaUrl() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", new byte[] { 1 });
        when(uploadPhotoUseCase.execute("user-1", file)).thenReturn("https://blob/foto.png");

        // Act
        ResponseEntity<String> response = profileController.uploadPhoto("user-1", file);

        // Assert
        assertThat(response.getBody()).isEqualTo("https://blob/foto.png");
    }

    @Test
    void switchRole_deberiaDelegarEnElUseCaseConElEmailYElNuevoRol() {
        // Arrange
        AuthResponseDTO expected = new AuthResponseDTO("a", "r", "Rol cambiado exitosamente a PROVIDER", "test@gmail.com");
        when(switchRoleUseCase.execute("test@gmail.com", Role.PROVIDER)).thenReturn(expected);

        // Act
        ResponseEntity<AuthResponseDTO> response = profileController.switchRole("test@gmail.com", Role.PROVIDER);

        // Assert
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void deleteAccount_deberiaDelegarEnElUseCaseYRetornarUnMensaje() {
        // Act
        ResponseEntity<Map<String, String>> response = profileController.deleteAccount("test@gmail.com");

        // Assert
        verify(deleteAccountUseCase).execute("test@gmail.com");
        assertThat(response.getBody().get("message")).contains("test@gmail.com");
    }

    @Test
    void searchProfiles_deberiaMapearLaListaDePerfilesADtos() {
        // Arrange
        Profile profile = Profile.builder().userId("user-1").fullName("Robin").build();
        when(searchProfileUseCase.execute("Robin")).thenReturn(List.of(profile));

        // Act
        ResponseEntity<List<ProfileResponseDTO>> response = profileController.searchProfiles("Robin");

        // Assert
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getFullName()).isEqualTo("Robin");
    }

    @Test
    void uploadDocument_deberiaDelegarEnElUseCaseYRetornarElPerfilActualizado() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "cedula.png", "image/png", new byte[] { 1 });
        Profile updated = Profile.builder().userId("user-1").documentBlobName("user-1/abc.png")
                .identityVerificationStatus(VerificationStatus.PENDING_REVIEW).build();
        when(submitIdentityDocumentUseCase.execute("user-1", DocumentType.PASSPORT, file)).thenReturn(updated);

        // Act
        ResponseEntity<ProfileResponseDTO> response = profileController.uploadDocument("user-1", file,
                DocumentType.PASSPORT);

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getUserId()).isEqualTo("user-1");
        assertThat(response.getBody().getIdentityVerificationStatus()).isEqualTo(VerificationStatus.PENDING_REVIEW);
    }

    @Test
    void updateProfile_deberiaDelegarEnElUseCaseConTodosLosParametros() {
        // Arrange
        List<String> categories = List.of("plomería", "electricidad");
        Profile updated = Profile.builder().userId("user-1").description("nueva").photoUrl("https://blob/foto.png")
                .locationCoverage("Bogotá").categories(categories).build();
        when(updateProfileUseCase.updateProfile("user-1", "nueva", "https://blob/foto.png", "Bogotá", categories))
                .thenReturn(updated);

        // Act
        ResponseEntity<ProfileResponseDTO> response = profileController.updateProfile("user-1", "nueva",
                "https://blob/foto.png", "Bogotá", categories);

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getDescription()).isEqualTo("nueva");
        assertThat(response.getBody().getPhotoUrl()).isEqualTo("https://blob/foto.png");
        assertThat(response.getBody().getCategories()).isEqualTo(categories);
        verify(updateProfileUseCase).updateProfile("user-1", "nueva", "https://blob/foto.png", "Bogotá", categories);
    }
}
