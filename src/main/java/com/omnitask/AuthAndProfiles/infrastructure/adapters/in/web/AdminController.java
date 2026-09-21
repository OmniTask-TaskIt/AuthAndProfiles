package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web;

import org.springframework.web.bind.annotation.PostMapping;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ResolveVerificationRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ProfileResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.DocumentAccessResponseDTO;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.application.usecases.ResolveIdentityVerificationUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.GetIdentityDocumentAccessUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.ChangeAccountStatusUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.ListUsersUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AdminUserDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.UpdateAccountStatusRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de administración: solo accesibles con rol ADMIN. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final ListUsersUseCase listUsersUseCase;
    private final ChangeAccountStatusUseCase changeAccountStatusUseCase;
    private final GetIdentityDocumentAccessUseCase getIdentityDocumentAccessUseCase;
    private final ResolveIdentityVerificationUseCase resolveIdentityVerificationUseCase;

    @GetMapping("/users")
    public ResponseEntity<PageResponseDTO<AdminUserDTO>> listUsers(
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false, name = "q") String emailQuery,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(listUsersUseCase.execute(status, emailQuery, page, size));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<AdminUserDTO> updateStatus(
            @PathVariable String userId,
            @Valid @RequestBody UpdateAccountStatusRequestDTO request,
            Authentication authentication) {
        User updated = changeAccountStatusUseCase.execute(userId, request.getStatus(), request.getReason(),
                authentication.getName());
        return ResponseEntity.ok(AdminUserDTO.from(updated));
    }

    /**
     * Enlace temporal (unos minutos) de solo lectura al documento de identidad de un usuario, para quien
     * revisa la verificación. Cada acceso queda registrado como evento de auditoría.
     */
    @GetMapping("/verification-documents/{userId}/access")
    public ResponseEntity<DocumentAccessResponseDTO> getDocumentAccess(
            @PathVariable String userId,
            Authentication authentication) {
        return ResponseEntity.ok(getIdentityDocumentAccessUseCase.execute(userId, authentication.getName()));
    }

    /**
     * Resolución manual de la verificación (aprobar o rechazar). Usa la misma lógica que el evento
     * IdentityVerificationResolved que llega desde HITL; sirve mientras ese servicio no esté disponible.
     */
    @PostMapping("/verification-documents/{userId}/resolution")
    public ResponseEntity<ProfileResponseDTO> resolveVerification(
            @PathVariable String userId,
            @Valid @RequestBody ResolveVerificationRequestDTO request,
            Authentication authentication) {
        Profile profile = resolveIdentityVerificationUseCase.execute(userId, request.getDecision(),
                request.getReason(), authentication.getName());
        return ResponseEntity.ok(ProfileResponseDTO.fromProfile(profile));
    }
}
