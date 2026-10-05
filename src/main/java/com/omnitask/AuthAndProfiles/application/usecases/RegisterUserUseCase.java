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
        String otpKey = "otp:" + request.getEmail();

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

        // El guardado del usuario es la "compuerta" de unicidad: si falla (p. ej. dos registros
        // simultáneos del mismo correo) todavía no se tocó Redis ni se envió nada que deshacer.
        User savedUser = userRepository.save(newUser);

        // A partir de aquí hay varios sistemas externos (Mongo, Redis, Resend, outbox) sin transacción
        // común. Si alguno falla, se revierte lo ya creado para que el registro sea "todo o nada":
        // antes, un fallo del correo (502) dejaba una cuenta PENDING_VERIFICATION sin OTP.
        try {
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

            // El OTP se guarda ANTES de enviar el correo: nunca se manda un código que no se pueda verificar.
            tokenRedisRepository.saveRefreshToken(otpKey, otpCode, 600000);

            resendEmailService.sendOtpEmail(savedUser.getEmail(), otpCode);

            // El evento va al final: solo se anuncia un USER_REGISTERED de un registro que se completó.
            eventPublisher.publish(EventType.USER_REGISTERED, savedUser.getId(),
                    new UserRegisteredEvent(savedUser.getId(), savedUser.getEmail(), savedUser.getName(),
                            savedUser.getRole().name(), AuthProvider.LOCAL.name(), Instant.now()));
        } catch (RuntimeException e) {
            log.error("[AUDIT] Registro fallido para {}; se revierte la cuenta creada: {}",
                    savedUser.getEmail(), e.getMessage());
            rollbackRegistration(savedUser, otpKey);
            throw e;
        }

        log.info("[AUDIT] [SEC-AUTH-02] Registro exitoso con aceptación de términos v1.0. OTP enviado a ID: {}",
                savedUser.getId());
        return savedUser;
    }

    /**
     * Deshace lo creado por un registro que no se pudo completar. Cada paso va por separado para que el
     * fallo de uno (p. ej. Redis caído) no impida intentar los demás ni oculte la excepción original.
     */
    private void rollbackRegistration(User savedUser, String otpKey) {
        try {
            tokenRedisRepository.deleteRefreshToken(otpKey);
        } catch (RuntimeException ex) {
            log.error("[AUDIT] No se pudo borrar el OTP de {} al revertir el registro: {}", savedUser.getEmail(),
                    ex.getMessage());
        }
        try {
            profileRepository.findByUserId(savedUser.getId()).ifPresent(profileRepository::delete);
        } catch (RuntimeException ex) {
            log.error("[AUDIT] No se pudo borrar el perfil de {} al revertir el registro: {}", savedUser.getEmail(),
                    ex.getMessage());
        }
        try {
            userRepository.delete(savedUser);
        } catch (RuntimeException ex) {
            log.error("[AUDIT] No se pudo borrar el usuario {} al revertir el registro: {}", savedUser.getEmail(),
                    ex.getMessage());
        }
    }
}