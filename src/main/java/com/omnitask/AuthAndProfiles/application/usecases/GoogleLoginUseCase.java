package com.omnitask.AuthAndProfiles.application.usecases;

import java.time.Instant;
import com.omnitask.AuthAndProfiles.domain.events.UserRegisteredEvent;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.application.services.GoogleAuthService;
import com.omnitask.AuthAndProfiles.application.services.ClientContext;
import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.application.services.SessionTokens;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.policies.AccountAccessPolicy;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleLoginUseCase {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final GoogleAuthService googleAuthService;
    private final SessionService sessionService;
    private final EventPublisher eventPublisher;

    public AuthResponseDTO execute(String googleToken, boolean acceptedTerms, ClientContext context) {
        var payload = googleAuthService.verifyToken(googleToken);
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        log.info("[AUDIT] [SEC-AUTH-03] Intento de login con Google para el correo: {}", email);

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            if (!acceptedTerms) {
                log.warn("[SECURITY] Intento de alta con Google sin aceptar términos para el correo: {}", email);
                throw new IllegalArgumentException("Es obligatorio aceptar los términos y condiciones.");
            }
            log.info("[AUDIT] Creando nuevo usuario a partir de cuenta de Google: {}", email);
            User newUser = User.builder()
                    .email(email)
                    .name(name)
                    .passwordHash("")
                    .role(Role.SEEKER)
                    .authProvider(AuthProvider.GOOGLE)
                    .emailVerified(true)
                    .termsAccepted(acceptedTerms)
                    .termsAcceptedAt(LocalDateTime.now())
                    .termsVersion("1.0")
                    .accountStatus(AccountStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            User savedUser = userRepository.save(newUser);

            Profile initialProfile = Profile.builder()
                    .userId(savedUser.getId())
                    .fullName(savedUser.getName())
                    .currentRole(savedUser.getRole().name())
                    .reputationScore(5.0f)
                    .totalReviews(0)
                    .description("")
                    .locationCoverage("")
                    .photoUrl("")
                    .documentUrl("")
                    .categories(new ArrayList<>())
                    .identityVerificationStatus(VerificationStatus.UNVERIFIED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            profileRepository.save(initialProfile);
            eventPublisher.publish(EventType.USER_REGISTERED, savedUser.getId(),
                    new UserRegisteredEvent(savedUser.getId(), savedUser.getEmail(), savedUser.getName(),
                            savedUser.getRole().name(), AuthProvider.GOOGLE.name(), Instant.now()));
            log.info("[AUDIT] Perfil inicial creado para el usuario de Google: {}", savedUser.getId());

            return savedUser;
        });

        AccountAccessPolicy.ensureNotRestricted(user);

        SessionTokens tokens = sessionService.openSession(user, context);

        log.info("[AUTH] Login con Google exitoso para: {}", user.getEmail());
        return new AuthResponseDTO(tokens.accessToken(), tokens.refreshToken(), "Autenticación con Google exitosa",
                user.getEmail());
    }
}