package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo;

import com.omnitask.AuthAndProfiles.domain.models.UserSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends MongoRepository<UserSession, String> {

    /** Sesiones vigentes del usuario, de la menos reciente a la más reciente (orden usado por la política LRU). */
    List<UserSession> findByUserEmailAndRevokedFalseAndExpiresAtAfterOrderByLastActiveAtAsc(String userEmail,
            LocalDateTime now);

    /** Busca por id solo dentro de las sesiones del usuario: nadie puede ver ni cerrar sesiones ajenas. */
    Optional<UserSession> findByIdAndUserEmail(String id, String userEmail);

    /** Historial: ¿el usuario ha iniciado sesión alguna vez (en los últimos 90 días)? */
    boolean existsByUserEmail(String userEmail);

    /** Historial: ¿este dispositivo ya había iniciado sesión con esta cuenta? */
    boolean existsByUserEmailAndDeviceId(String userEmail, String deviceId);
}
