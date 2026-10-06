
package com.omnitask.AuthAndProfiles.domain.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Sesión de un usuario en un dispositivo (RF-AUTH-10). Se crea en cada inicio de sesión y su id viaja en el
 * claim "sid" de los tokens. Las sesiones revocadas o vencidas se conservan como historial (para reconocer
 * dispositivos conocidos, RF-AUTH-11) hasta que Mongo las elimina por el índice TTL de createdAt (90 días).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_sessions")
public class UserSession {

    @Id
    private String id;

    @Indexed
    private String userEmail;

    /** Identificador estable del dispositivo: X-Device-Id si llega, o "ua:" + descripción del User-Agent. */
    private String deviceId;
    /** Descripción legible, por ejemplo "Chrome en Windows". */
    private String deviceInfo;
    private String ipAddress;

    @Indexed(expireAfterSeconds = 7_776_000)
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime expiresAt;

    private boolean revoked;
    private LocalDateTime revokedAt;
    /** LOGOUT, REMOTE_LOGOUT, REPLACED_SAME_DEVICE o DEVICE_LIMIT. */
    private String revokedReason;
}
