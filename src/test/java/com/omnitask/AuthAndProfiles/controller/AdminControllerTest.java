package com.omnitask.AuthAndProfiles.controller;

import java.time.Instant;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ResolveVerificationRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ProfileResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.DocumentAccessResponseDTO;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationDecision;
import com.omnitask.AuthAndProfiles.application.usecases.ResolveIdentityVerificationUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.GetIdentityDocumentAccessUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.ChangeAccountStatusUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.ListUsersUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.AdminController;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AdminUserDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.UpdateAccountStatusRequestDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private ListUsersUseCase listUsersUseCase;
    @Mock
    private ChangeAccountStatusUseCase changeAccountStatusUseCase;
    @Mock
    private GetIdentityDocumentAccessUseCase getIdentityDocumentAccessUseCase;
    @Mock
    private ResolveIdentityVerificationUseCase resolveIdentityVerificationUseCase;

    @InjectMocks
    private AdminController adminController;

    @Test
    void listUsers_deberiaDelegarConLosFiltrosYRetornar200() {
        PageResponseDTO<AdminUserDTO> page = new PageResponseDTO<>(List.of(), 0, 20, 0, 0);
        when(listUsersUseCase.execute(AccountStatus.SUSPENDED, "test", 1, 10)).thenReturn(page);

        ResponseEntity<PageResponseDTO<AdminUserDTO>> response = adminController.listUsers(AccountStatus.SUSPENDED,
                "test", 1, 10);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(page);
    }

    @Test
    void updateStatus_deberiaCambiarElEstadoConElAdministradorAutenticadoComoActor() {
        UpdateAccountStatusRequestDTO body = new UpdateAccountStatusRequestDTO();
        body.setStatus(AccountStatus.SUSPENDED);
        body.setReason("Fraude");
        Authentication auth = new UsernamePasswordAuthenticationToken("admin@omnitask.com", null, List.of());
        User updated = User.builder().id("user-1").email("test@gmail.com").role(Role.PROVIDER)
                .accountStatus(AccountStatus.SUSPENDED).blockReason("Fraude").passwordHash("secreto").build();
        when(changeAccountStatusUseCase.execute("user-1", AccountStatus.SUSPENDED, "Fraude", "admin@omnitask.com"))
                .thenReturn(updated);

        ResponseEntity<AdminUserDTO> response = adminController.updateStatus("user-1", body, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().accountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(response.getBody().blockReason()).isEqualTo("Fraude");
        verify(changeAccountStatusUseCase).execute("user-1", AccountStatus.SUSPENDED, "Fraude",
                "admin@omnitask.com");
    }

    @Test
    void getDocumentAccess_deberiaEntregarElEnlaceTemporalUsandoAlAdminComoActor() {
        Authentication auth = new UsernamePasswordAuthenticationToken("admin@omnitask.com", null, List.of());
        DocumentAccessResponseDTO dto = new DocumentAccessResponseDTO("https://blob/doc?sig=x", Instant.now(),
                "CEDULA", "application/pdf", null);
        when(getIdentityDocumentAccessUseCase.execute("user-1", "admin@omnitask.com")).thenReturn(dto);

        ResponseEntity<DocumentAccessResponseDTO> response = adminController.getDocumentAccess("user-1", auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(dto);
    }

    @Test
    void resolveVerification_deberiaAplicarLaDecisionYRetornarElPerfilSinDatosSensibles() {
        ResolveVerificationRequestDTO body = new ResolveVerificationRequestDTO();
        body.setDecision(VerificationDecision.REJECTED);
        body.setReason("Documento ilegible");
        Authentication auth = new UsernamePasswordAuthenticationToken("admin@omnitask.com", null, List.of());
        Profile profile = Profile.builder().userId("user-1").fullName("Test")
                .identityVerificationStatus(VerificationStatus.REJECTED).build();
        when(resolveIdentityVerificationUseCase.execute("user-1", VerificationDecision.REJECTED,
                "Documento ilegible", "admin@omnitask.com")).thenReturn(profile);

        ResponseEntity<ProfileResponseDTO> response = adminController.resolveVerification("user-1", body, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getIdentityVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(response.getBody().getUserId()).isEqualTo("user-1");
    }
}
