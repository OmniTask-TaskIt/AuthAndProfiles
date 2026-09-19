package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendOtpUseCase {

    private final UserRepository userRepository;
    private final TokenRedisRepository tokenRedisRepository;
    private final ResendEmailService resendEmailService;

    public void execute(String email) {
        log.info("[AUDIT] Solicitud de reenvío de OTP para el email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con este correo"));

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Este correo ya ha sido verificado anteriormente.");
        }

        String newOtpCode = String.format("%06d", new java.util.Random().nextInt(999999));

        resendEmailService.sendOtpEmail(user.getEmail(), newOtpCode);

        String otpKey = "otp:" + user.getEmail();
        tokenRedisRepository.saveRefreshToken(otpKey, newOtpCode, 600000);

        log.info("[AUDIT] Nuevo código OTP reenviado exitosamente a: {}", user.getEmail());
    }
}