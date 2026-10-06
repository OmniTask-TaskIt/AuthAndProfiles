package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import com.omnitask.AuthAndProfiles.domain.models.UserSession;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Sesión activa tal como la ve el usuario; "current" marca la sesión desde la que hace la consulta. Las fechas viajan
 * como Instant (ISO-8601 con zona) para que el navegador las muestre en la hora local sin importar la zona del servidor.
 */
@Data
@Builder
public class SessionResponseDTO {
    private String id;
    private String deviceInfo;
    private String ipAddress;
    private Instant createdAt;
    private Instant lastActiveAt;
    private boolean current;

    public static SessionResponseDTO fromSession(UserSession session, boolean current) {
        return SessionResponseDTO.builder()
                .id(session.getId())
                .deviceInfo(session.getDeviceInfo())
                .ipAddress(session.getIpAddress())
                .createdAt(toInstant(session.getCreatedAt()))
                .lastActiveAt(toInstant(session.getLastActiveAt()))
                .current(current)
                .build();
    }

    private static Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
