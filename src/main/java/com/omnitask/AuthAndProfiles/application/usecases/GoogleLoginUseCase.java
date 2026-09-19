package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.GoogleAuthService;
import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
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
    private final JwtService jwtService;
    private final TokenRedisRepository tokenRedisRepository;

    public AuthResponseDTO execute(String googleToken) {
        var payload = googleAuthService.verifyToken(googleToken);
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        log.info("[AUDIT] [SEC-AUTH-03] Intento de login con Google para el correo: {}", email);

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            log.info("[AUDIT] Creando nuevo usuario a partir de cuenta de Google: {}", email);
            User newUser = User.builder()
                    .email(email)
                    .name(name)
                    .passwordHash("")
                    .role(Role.SEEKER)
                    .authProvider(AuthProvider.GOOGLE)
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
                    .documentUrl("")
                    .categories(new ArrayList<>())
                    .identityVerificationStatus(VerificationStatus.PENDING_REVIEW)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            profileRepository.save(initialProfile);
            log.info("[AUDIT] Perfil inicial creado para el usuario de Google: {}", savedUser.getId());

            return savedUser;
        });

        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        tokenRedisRepository.saveRefreshToken(user.getEmail(), refreshToken, 604800000);

        log.info("[AUTH] Login con Google exitoso para: {}", user.getEmail());
        return new AuthResponseDTO(accessToken, refreshToken, "Autenticación con Google exitosa", user.getEmail());
    }
}