package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.GithubAuthService;
import com.omnitask.AuthAndProfiles.application.services.GithubProfile;
import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.UserRegisteredEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.AuthenticationFailedException;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.policies.AccountAccessPolicy;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Login con GitHub OAuth (RF-AUTH-13). El front redirige al usuario a GitHub, recibe un "code" y lo manda
 * aquí. Se intercambia por un access token, se consulta el perfil (login, nombre, correo verificado) y,
 * como con Google, si el correo no existe se crea la cuenta y el perfil inicial en el mismo flujo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GithubLoginUseCase {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final GithubAuthService githubAuthService;
    private final JwtService jwtService;
    private final TokenRedisRepository tokenRedisRepository;
    private final EventPublisher eventPublisher;

    public AuthResponseDTO execute(String code) {
        String accessToken = githubAuthService.exchangeCodeForAccessToken(code);
        GithubProfile profile = githubAuthService.fetchProfile(accessToken);

        String email = profile.email();
        if (email == null || email.isBlank()) {
            throw new AuthenticationFailedException("Tu cuenta de GitHub no tiene un correo verificado disponible.");
        }
        String name = (profile.name() != null && !profile.name().isBlank()) ? profile.name() : profile.login();

        log.info("[AUDIT] [SEC-AUTH-03] Intento de login con GitHub para el correo: {}", email);

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            log.info("[AUDIT] Creando nuevo usuario a partir de cuenta de GitHub: {}", email);
            User newUser = User.builder()
                    .email(email)
                    .name(name)
                    .passwordHash("")
                    .role(Role.SEEKER)
                    .authProvider(AuthProvider.GITHUB)
                    .emailVerified(true)
                    .termsAccepted(true)
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
                    .categories(new ArrayList<>())
                    .identityVerificationStatus(VerificationStatus.UNVERIFIED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            profileRepository.save(initialProfile);
            eventPublisher.publish(EventType.USER_REGISTERED, savedUser.getId(),
                    new UserRegisteredEvent(savedUser.getId(), savedUser.getEmail(), savedUser.getName(),
                            savedUser.getRole().name(), AuthProvider.GITHUB.name(), Instant.now()));
            log.info("[AUDIT] Perfil inicial creado para el usuario de GitHub: {}", savedUser.getId());

            return savedUser;
        });

        AccountAccessPolicy.ensureNotRestricted(user);

        String jwtAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        tokenRedisRepository.saveRefreshToken(user.getEmail(), refreshToken, 604800000);

        log.info("[AUTH] Login con GitHub exitoso para: {}", user.getEmail());
        return new AuthResponseDTO(jwtAccessToken, refreshToken, "Autenticación con GitHub exitosa",
                user.getEmail());
    }
}
