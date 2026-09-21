package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.AzureBlobService;
import com.omnitask.AuthAndProfiles.application.services.IdentityDocumentValidator;
import com.omnitask.AuthAndProfiles.application.services.ValidatedDocument;
import com.omnitask.AuthAndProfiles.domain.enums.DocumentType;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.IdentityDocumentSubmittedEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * El usuario envía su documento de identidad a verificación: se valida, se guarda en el contenedor privado
 * de Azure Blob, el perfil pasa a PENDING_REVIEW y se publica IdentityDocumentSubmitted para que
 * Security and Audit HITL lo revise.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmitIdentityDocumentUseCase {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final IdentityDocumentValidator identityDocumentValidator;
    private final AzureBlobService azureBlobService;
    private final EventPublisher eventPublisher;

    public Profile execute(String userId, DocumentType documentType, MultipartFile file) {
        ValidatedDocument document = identityDocumentValidator.validate(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Profile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Perfil no encontrado para este usuario"));

        if (profile.getIdentityVerificationStatus() == VerificationStatus.VERIFIED) {
            throw new IllegalArgumentException("Tu identidad ya está verificada.");
        }
        if (profile.getIdentityVerificationStatus() == VerificationStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException(
                    "Ya tienes un documento en revisión. Espera la respuesta del equipo de verificación.");
        }

        String previousBlobName = profile.getDocumentBlobName();
        String blobName = azureBlobService.uploadIdentityDocument(userId, file, document);
        if (previousBlobName != null && !previousBlobName.isBlank()) {
            azureBlobService.deleteIdentityDocument(previousBlobName);
        }

        profile.setDocumentBlobName(blobName);
        profile.setDocumentType(documentType.name());
        profile.setDocumentContentType(document.contentType());
        profile.setDocumentSizeBytes(file.getSize());
        profile.setDocumentSubmittedAt(LocalDateTime.now());
        profile.setIdentityVerificationStatus(VerificationStatus.PENDING_REVIEW);
        profile.setVerificationReason(null);
        profile.setUpdatedAt(LocalDateTime.now());
        Profile saved = profileRepository.save(profile);

        eventPublisher.publish(EventType.IDENTITY_DOCUMENT_SUBMITTED, userId,
                new IdentityDocumentSubmittedEvent(userId, user.getEmail(), profile.getFullName(),
                        documentType.name(), document.contentType(), file.getSize(), blobName, Instant.now()));

        log.info("[AUDIT] [SEC-AUTH-04] Documento de identidad enviado a revisión HITL para el usuario ID: {}",
                userId);
        return saved;
    }
}
