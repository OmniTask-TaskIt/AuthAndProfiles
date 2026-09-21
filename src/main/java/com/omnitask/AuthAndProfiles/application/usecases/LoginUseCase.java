package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.IpRateLimiterService;
import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.exceptions.AccountRestrictedException;
import com.omnitask.AuthAndProfiles.domain.exceptions.AuthenticationFailedException;
import com.omnitask.AuthAndProfiles.domain.exceptions.TooManyAttemptsException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.policies.AccountAccessPolicy;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.LoginRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRedisRepository tokenRedisRepository;
    private final IpRateLimiterService ipRateLimiterService;

    public AuthResponseDTO execute(LoginRequestDTO request, String clientIp) {
        if (ipRateLimiterService.isBlocked(clientIp)) {
            log.warn("[SECURITY] [SEC-AUTH-06] Acceso denegado. IP bloqueada temporalmente: {}", clientIp);
            throw new TooManyAttemptsException(
                    "Demasiados intentos fallidos. Su dirección IP ha sido bloqueada temporalmente por 15 minutos.");
        }

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        // Un solo intento fallido por petición, exista o no el usuario (evita contar doble).
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            ipRateLimiterService.recordFailedAttempt(clientIp);
            log.warn("[SECURITY] [SEC-AUTH-04] Intento de acceso fallido para el correo: {} desde IP: {}",
                    request.getEmail(), clientIp);
            throw new AuthenticationFailedException("Credenciales inválidas");
        }

        // Solo se revela el estado de la cuenta cuando las credenciales ya fueron correctas.
        AccountAccessPolicy.ensureNotRestricted(user);

        if (!user.isEmailVerified()) {
            log.warn("[SECURITY] Intento de login sin verificar correo: {}", request.getEmail());
            throw new AccountRestrictedException(
                    "Debes verificar tu cuenta con el código OTP enviado a tu correo antes de iniciar sesión.");
        }

        ipRateLimiterService.resetAttempts(clientIp);

        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        tokenRedisRepository.saveRefreshToken(user.getEmail(), refreshToken, 604800000);

        log.info("[AUTH] [SEC-AUTH-02] Login exitoso para el usuario: {} desde IP: {}", user.getEmail(), clientIp);

        return new AuthResponseDTO(accessToken, refreshToken, "Inicio de sesión exitoso", user.getEmail());
    }
}
