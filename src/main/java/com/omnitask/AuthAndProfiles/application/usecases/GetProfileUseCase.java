package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetProfileUseCase {

    private final ProfileRepository profileRepository;

    public Profile execute(String userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Perfil no encontrado para el usuario especificado."));
    }
}
