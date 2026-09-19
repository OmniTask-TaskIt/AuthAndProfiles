package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.VerifyOtpRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyOtpUseCase {

    private final UserRepository userRepository;
    private final TokenRedisRepository tokenRedisRepository;

    public void execute(VerifyOtpRequestDTO request) {
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
}
