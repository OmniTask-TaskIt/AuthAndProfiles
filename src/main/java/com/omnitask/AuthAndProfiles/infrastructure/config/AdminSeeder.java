package com.omnitask.AuthAndProfiles.infrastructure.config;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Crea el administrador inicial. Las credenciales vienen del entorno (ADMIN_EMAIL, ADMIN_PASSWORD);
 * si no hay contraseña configurada no se crea ningún administrador.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.email:admin@omnitask.com}")
    private String adminEmail;

    @Value("${admin.seed.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("[SECURITY] ADMIN_PASSWORD no está configurada: no se inicializa el usuario administrador.");
            return;
        }

        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            User superAdmin = User.builder()
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .name("Super Administrator")
                    .role(Role.ADMIN)
                    .authProvider(AuthProvider.LOCAL)
                    .emailVerified(true)
                    .termsAccepted(true)
                    .termsAcceptedAt(LocalDateTime.now())
                    .termsVersion("1.0")
                    .accountStatus(AccountStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            userRepository.save(superAdmin);
            log.info("[SECURITY] [AUDIT] Usuario Administrador inicializado correctamente en el sistema.");
        }
    }
}
