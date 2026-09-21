package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.User;

import java.time.LocalDateTime;

/** Vista de un usuario para el panel de administración (sin hash de contraseña). */
public record AdminUserDTO(
        String id,
        String email,
        String name,
        Role role,
        AuthProvider authProvider,
        AccountStatus accountStatus,
        String blockReason,
        boolean emailVerified,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AdminUserDTO from(User user) {
        return new AdminUserDTO(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getAuthProvider(),
                user.getAccountStatus(),
                user.getBlockReason(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
