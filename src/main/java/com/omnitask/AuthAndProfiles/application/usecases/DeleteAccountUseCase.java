package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteAccountUseCase {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final TokenRedisRepository tokenRedisRepository;

    public void execute(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        tokenRedisRepository.saveRefreshToken(email, "", 1);
        profileRepository.findByUserId(user.getId()).ifPresent(profileRepository::delete);
        userRepository.delete(user);

        log.warn("[AUDIT-SECURITY] Cuenta eliminada permanentemente para el correo: {}", email);
    }
}
