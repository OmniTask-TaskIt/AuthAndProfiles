package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RF-AUTHPR-8: suma una tarea completada al historial del prestador cuando Task Service lo informa. La suma es
 * atómica en la base de datos y el consumidor de Kafka descarta los eventos repetidos por eventId, así que un
 * evento duplicado no cuenta dos veces.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordTaskCompletedUseCase {

    private final ProfileRepository profileRepository;

    public void execute(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("El evento TaskCompleted no trae providerId.");
        }
        long updated = profileRepository.incrementTasksCompletedByUserId(providerId);
        if (updated == 0) {
            // Un usuario sin perfil no se arregla reintentando: se registra y se sigue.
            log.warn("[AUDIT] TaskCompleted para el usuario {}, que no tiene perfil: no se actualizó ningún contador.",
                    providerId);
            return;
        }
        log.info("[AUDIT] Tarea completada sumada al historial del prestador {}", providerId);
    }
}
