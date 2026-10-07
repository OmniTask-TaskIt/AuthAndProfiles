package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.ClientContext;
import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.application.services.SessionTokens;
import com.omnitask.AuthAndProfiles.application.services.TwoFactorService;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.AuthenticationFailedException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.policies.AccountAccessPolicy;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.TwoFactorLoginRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * RF-AUTH-9, segundo paso del inicio de sesión: canjea el reto (challengeId) y el código enviado por correo por la
 * sesión del dispositivo. La cuenta se vuelve a validar porque pudo suspenderse entre el primer y el segundo paso.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyTwoFactorLoginUseCase {

    private final TwoFactorService twoFactorService;
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final EventPublisher eventPublisher;

    public AuthResponseDTO execute(TwoFactorLoginRequestDTO request, ClientContext context) {
        String email = twoFactorService.verifyLoginChallenge(request.getChallengeId(), request.getCode());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("Usuario no encontrado"));
        AccountAccessPolicy.ensureNotRestricted(user);

        SessionTokens tokens = sessionService.openSession(user, context);

        eventPublisher.publish(EventType.SECURITY_AUDIT, email,
                new SecurityAuditEvent("LOGIN_SUCCESS", email, null, context.ip(), Instant.now()));
        log.info("[AUTH] [SEC-AUTH-02] Login con segundo factor exitoso para: {} desde IP: {}", email, context.ip());

        return new AuthResponseDTO(tokens.accessToken(), tokens.refreshToken(), "Inicio de sesión exitoso", email);
    }
}
