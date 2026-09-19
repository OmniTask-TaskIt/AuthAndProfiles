package com.omnitask.AuthAndProfiles.application.services;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.LoginRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.RefreshTokenRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.RegisterRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.VerifyOtpRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRedisRepository tokenRedisRepository;
    private final ResendEmailService resendEmailService;
    private final IpRateLimiterService ipRateLimiterService;

    public User registerUser(RegisterRequestDTO request) {
        log.info("[AUDIT] [SEC-AUTH-01] Iniciando proceso de registro para email: {}", request.getEmail());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("[AUDIT-SECURITY] [SEC-AUTH-04] Intento de registro fallido: El email {} ya existe",
                    request.getEmail());
            throw new IllegalArgumentException("El usuario ya está registrado.");
        }

        if (!request.isAcceptedTerms()) {
            log.warn("[SECURITY] Intento de registro sin aceptar términos para el correo: {}", request.getEmail());
            throw new IllegalArgumentException("Es obligatorio aceptar los términos y condiciones.");
        }

        String otpCode = String.format("%06d", new java.util.Random().nextInt(999999));

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

        resendEmailService.sendOtpEmail(savedUser.getEmail(), otpCode);

        String otpKey = "otp:" + savedUser.getEmail();
        tokenRedisRepository.saveRefreshToken(otpKey, otpCode, 600000);

        log.info("[AUDIT] [SEC-AUTH-02] Registro exitoso con aceptación de términos v1.0. OTP enviado a ID: {}",
                savedUser.getId());

        return savedUser;
    }

    public void verifyEmail(VerifyOtpRequestDTO request) {
        String otpKey = "otp:" + request.getEmail();
        String storedOtp = tokenRedisRepository.getRefreshToken(otpKey);

        if (storedOtp == null || !storedOtp.equals(request.getOtpCode())) {
            log.warn("[SECURITY] [SEC-AUTH-04] Intento fallido de verificación OTP para el correo: {}",
                    request.getEmail());
            throw new RuntimeException("Código OTP inválido o expirado");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);

        tokenRedisRepository.saveRefreshToken(otpKey, "", 1);

        log.info("[AUDIT] [SEC-AUTH-02] Correo verificado exitosamente mediante OTP para: {}", user.getEmail());
    }

    public AuthResponseDTO login(LoginRequestDTO request, String clientIp) {
        if (ipRateLimiterService.isBlocked(clientIp)) {
            log.warn("[SECURITY] [SEC-AUTH-06] Acceso denegado. IP bloqueada temporalmente: {}", clientIp);
            throw new RuntimeException(
                    "Demasiados intentos fallidos. Su dirección IP ha sido bloqueada temporalmente por 15 minutos.");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseGet(() -> {
                    ipRateLimiterService.recordFailedAttempt(clientIp);
                    return null;
                });

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            ipRateLimiterService.recordFailedAttempt(clientIp);
            log.warn("[SECURITY] [SEC-AUTH-04] Intento de acceso fallido para el correo: {} desde IP: {}",
                    request.getEmail(), clientIp);
            throw new RuntimeException("Credenciales inválidas");
        }

        if (!user.isEmailVerified()) {
            log.warn("[SECURITY] Intento de login sin verificar correo: {}", request.getEmail());
            throw new RuntimeException(
                    "Debes verificar tu cuenta con el código OTP enviado a tu correo antes de iniciar sesión.");
        }

        ipRateLimiterService.resetAttempts(clientIp);

        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        tokenRedisRepository.saveRefreshToken(user.getEmail(), refreshToken, 604800000);

        log.info("[AUTH] [SEC-AUTH-02] Login exitoso para el usuario: {} desde IP: {}", user.getEmail(), clientIp);

        return new AuthResponseDTO(accessToken, refreshToken, "Inicio de sesión exitoso", user.getEmail());
    }

    public AuthResponseDTO refreshToken(RefreshTokenRequestDTO request) {
        String storedToken = tokenRedisRepository.getRefreshToken(request.getEmail());

        if (storedToken == null || !storedToken.equals(request.getRefreshToken())) {
            log.warn("[SECURITY] [SEC-AUTH-04] Intento de uso de Refresh Token inválido o no coincidente para: {}",
                    request.getEmail());
            throw new RuntimeException("Refresh token inválido o expirado");
        }

        if (!jwtService.isTokenValid(request.getRefreshToken(), request.getEmail())) {
            log.warn(
                    "[SECURITY] [SEC-AUTH-04] Intento de uso de Refresh Token no autorizado criptográficamente para: {}",
                    request.getEmail());
            throw new RuntimeException("Refresh token no autorizado");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name());

        log.info("[AUTH] [SEC-AUTH-02] Access Token renovado exitosamente para: {}", user.getEmail());

        return new AuthResponseDTO(newAccessToken, request.getRefreshToken(), "Token renovado con éxito",
                user.getEmail());
    }
}
