package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SwitchRoleUseCase {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthResponseDTO execute(String email, Role newRole) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        if (newRole == Role.ADMIN) {
            throw new IllegalArgumentException("No se puede asignar el rol de administrador por cambio dinámico.");
        }

        user.setRole(newRole);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        return new AuthResponseDTO(newAccessToken, refreshToken, "Rol cambiado exitosamente a " + newRole.name(),
                user.getEmail());
    }
}
