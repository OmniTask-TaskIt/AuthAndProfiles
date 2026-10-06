package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.domain.models.UserSession;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.SessionResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/** RF-AUTH-10: lista las sesiones activas del usuario, de la más reciente a la más antigua. */
@Service
@RequiredArgsConstructor
public class ListSessionsUseCase {

    private final SessionService sessionService;
    private final JwtService jwtService;

    public List<SessionResponseDTO> execute(String email, String accessToken) {
        String currentSessionId = jwtService.extractSessionId(accessToken);
        return sessionService.listActive(email).stream()
                .sorted(Comparator.comparing(UserSession::getLastActiveAt).reversed())
                .map(session -> SessionResponseDTO.fromSession(session, session.getId().equals(currentSessionId)))
                .toList();
    }
}
