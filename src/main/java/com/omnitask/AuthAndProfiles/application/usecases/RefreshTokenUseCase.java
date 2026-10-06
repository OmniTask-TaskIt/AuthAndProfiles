package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.domain.exceptions.AuthenticationFailedException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.models.UserSession;
import com.omnitask.AuthAndProfiles.domain.policies.AccountAccessPolicy;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.RefreshTokenRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Renueva el access token. El refresh token debe ser criptográficamente válido, pertenecer a una sesión vigente
 * (claim "sid", RF-AUTH-10) y la cuenta debe conservar su marcador global de sesión en Redis, que se borra al
 * restablecer la contraseña, eliminar o suspender la cuenta. Los tokens emitidos antes de las sesiones (sin "sid")
 * ya no sirven: el usuario simplemente inicia sesión de nuevo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenUseCase {

    private final TokenRedisRepository tokenRedisRepository;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SessionService sessionService;

    public AuthResponseDTO execute(RefreshTokenRequestDTO request) {
        String email = request.getEmail();

        if (tokenRedisRepository.getRefreshToken(email) == null) {
            log.warn("[SECURITY] [SEC-AUTH-04] Intento de uso de Refresh Token inválido o no coincidente para: {}",
                    email);
            throw new AuthenticationFailedException("Refresh token inválido o expirado");
        }

        String sessionId;
        try {
            if (!jwtService.isTokenValid(request.getRefreshToken(), email)) {
                throw new AuthenticationFailedException("Refresh token no autorizado");
            }
            sessionId = jwtService.extractSessionId(request.getRefreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[SECURITY] [SEC-AUTH-04] Refresh Token malformado o vencido para: {}", email);
            throw new AuthenticationFailedException("Refresh token no autorizado");
        }

        UserSession session = sessionId == null ? null : sessionService.findActive(email, sessionId).orElse(null);
        if (session == null) {
            log.warn("[SECURITY] [SEC-AUTH-04] Refresh Token de una sesión cerrada, vencida o inexistente para: {}",
                    email);
            throw new AuthenticationFailedException("Refresh token inválido o expirado");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("Usuario no encontrado"));

        AccountAccessPolicy.ensureNotRestricted(user);

        sessionService.touch(session);
        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name(), sessionId);
        log.info("[AUTH] [SEC-AUTH-02] Access Token renovado exitosamente para: {}", user.getEmail());

        return new AuthResponseDTO(newAccessToken, request.getRefreshToken(), "Token renovado con éxito",
                user.getEmail());
    }
}
