package com.omnitask.AuthAndProfiles.application.usecases;

import java.time.Instant;
import com.omnitask.AuthAndProfiles.application.services.AzureBlobService;
import com.omnitask.AuthAndProfiles.domain.events.AccountDeletedEvent;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteAccountUseCase {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final TokenRedisRepository tokenRedisRepository;
    private final AzureBlobService azureBlobService;
    private final EventPublisher eventPublisher;

    public void execute(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        tokenRedisRepository.deleteRefreshToken(email);
        profileRepository.findByUserId(user.getId()).ifPresent(profile -> {
            String blobName = profile.getDocumentBlobName();
            if (blobName != null && !blobName.isBlank()) {
                azureBlobService.deleteIdentityDocument(blobName);
            }
            profileRepository.delete(profile);
        });
        userRepository.delete(user);

        eventPublisher.publish(EventType.ACCOUNT_DELETED, user.getId(),
                new AccountDeletedEvent(user.getId(), email, Instant.now()));

        log.warn("[AUDIT-SECURITY] Cuenta eliminada permanentemente para el correo: {}", email);
    }
}
