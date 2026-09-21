package com.omnitask.AuthAndProfiles.application.usecases;

import java.time.Instant;
import com.omnitask.AuthAndProfiles.domain.events.UserRegisteredEvent;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.application.services.OtpGenerator;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.RegisterRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRedisRepository tokenRedisRepository;
    private final ResendEmailService resendEmailService;
    private final ProfileRepository profileRepository;
    private final OtpGenerator otpGenerator;
    private final EventPublisher eventPublisher;

    public User execute(RegisterRequestDTO request) {
        log.info("[AUDIT] [SEC-AUTH-01] Iniciando proceso de registro para email: {}", request.getEmail());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("[AUDIT-SECURITY] [SEC-AUTH-04] Intento de registro fallido: El email {} ya existe",
                    request.getEmail());
            throw new IllegalArgumentException("El usuario ya está registrado.");
        }

        if (request.getRole() == Role.ADMIN) {
            log.warn("[SECURITY] Intento de registro con rol ADMIN para el correo: {}", request.getEmail());
            throw new IllegalArgumentException("Solo se permite registrarse como SEEKER o PROVIDER.");
        }

        if (!request.isAcceptedTerms()) {
            log.warn("[SECURITY] Intento de registro sin aceptar términos para el correo: {}", request.getEmail());
            throw new IllegalArgumentException("Es obligatorio aceptar los términos y condiciones.");
        }

        String otpCode = otpGenerator.generate();

        User newUser = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(request.getRole())
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .termsAccepted(request.isAcceptedTerms())
                .termsAcceptedAt(LocalDateTime.now())
                .termsVersion("1.0")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
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
                        savedUser.getRole().name(), AuthProvider.LOCAL.name(), Instant.now()));

        resendEmailService.sendOtpEmail(savedUser.getEmail(), otpCode);

        String otpKey = "otp:" + savedUser.getEmail();
        tokenRedisRepository.saveRefreshToken(otpKey, otpCode, 600000);

        log.info("[AUDIT] [SEC-AUTH-02] Registro exitoso con aceptación de términos v1.0. OTP enviado a ID: {}",
                savedUser.getId());
        return savedUser;
    }
}