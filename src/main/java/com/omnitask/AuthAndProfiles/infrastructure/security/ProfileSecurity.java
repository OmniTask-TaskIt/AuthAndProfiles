package com.omnitask.AuthAndProfiles.infrastructure.security;

import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Reglas de propiedad usadas desde @PreAuthorize, por ejemplo:
 * {@code @PreAuthorize("@profileSecurity.isOwner(#userId, authentication)")}.
 */
@Component("profileSecurity")
@RequiredArgsConstructor
public class ProfileSecurity {

    private final UserRepository userRepository;

    /** true si el usuario autenticado es el dueño del perfil identificado por userId. */
    public boolean isOwner(String userId, Authentication authentication) {
        if (userId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return userRepository.findByEmail(authentication.getName())
                .map(User::getId)
                .filter(userId::equals)
                .isPresent();
    }
}
