package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.exceptions.AuthenticationFailedException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.policies.AccountAccessPolicy;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.RefreshTokenRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenUseCase {

    private final TokenRedisRepository tokenRedisRepository;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthResponseDTO execute(RefreshTokenRequestDTO request) {
        String storedToken = tokenRedisRepository.getRefreshToken(request.getEmail());

        if (storedToken == null || !storedToken.equals(request.getRefreshToken())) {
            log.warn("[SECURITY] [SEC-AUTH-04] Intento de uso de Refresh Token inválido o no coincidente para: {}",
                    request.getEmail());
            throw new AuthenticationFailedException("Refresh token inválido o expirado");
        }

        if (!jwtService.isTokenValid(request.getRefreshToken(), request.getEmail())) {
            log.warn(
                    "[SECURITY] [SEC-AUTH-04] Intento de uso de Refresh Token no autorizado criptográficamente para: {}",
                    request.getEmail());
            throw new AuthenticationFailedException("Refresh token no autorizado");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthenticationFailedException("Usuario no encontrado"));

        AccountAccessPolicy.ensureNotRestricted(user);

        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name());
        log.info("[AUTH] [SEC-AUTH-02] Access Token renovado exitosamente para: {}", user.getEmail());

        return new AuthResponseDTO(newAccessToken, request.getRefreshToken(), "Token renovado con éxito",
                user.getEmail());
    }
}
