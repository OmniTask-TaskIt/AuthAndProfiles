package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** RF-AUTH-10: cierra de forma remota una sesión del usuario sin afectar las demás. */
@Service
@RequiredArgsConstructor
public class RevokeSessionUseCase {

    private final SessionService sessionService;

    public void execute(String email, String sessionId) {
        if (!sessionService.revoke(email, sessionId, "REMOTE_LOGOUT")) {
            throw new NotFoundException("Sesión no encontrada");
        }
    }
}
