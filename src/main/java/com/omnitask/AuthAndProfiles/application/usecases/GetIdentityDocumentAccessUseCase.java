package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.AzureBlobService;
import com.omnitask.AuthAndProfiles.application.services.SignedUrl;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.DocumentAccessResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Entrega a quien revisa (ADMIN) un enlace temporal de solo lectura al documento de identidad y deja
 * constancia del acceso en el log y como evento de auditoría.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetIdentityDocumentAccessUseCase {

    private final ProfileRepository profileRepository;
    private final AzureBlobService azureBlobService;
    private final EventPublisher eventPublisher;

    public DocumentAccessResponseDTO execute(String userId, String actorEmail) {
        Profile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Perfil no encontrado para este usuario"));

        String blobName = profile.getDocumentBlobName();
        if (blobName == null || blobName.isBlank()) {
            throw new NotFoundException("Este usuario no tiene un documento de identidad cargado.");
        }

        SignedUrl signedUrl = azureBlobService.generateIdentityDocumentReadUrl(blobName);

        log.warn("[AUDIT-SECURITY] {} accedió al documento de identidad del usuario {}", actorEmail, userId);
        eventPublisher.publish(EventType.SECURITY_AUDIT, userId,
                new SecurityAuditEvent("IDENTITY_DOCUMENT_ACCESSED", userId, actorEmail, null, Instant.now()));

        return new DocumentAccessResponseDTO(signedUrl.url(), signedUrl.expiresAt(), profile.getDocumentType(),
                profile.getDocumentContentType(), profile.getDocumentSubmittedAt());
    }
}
