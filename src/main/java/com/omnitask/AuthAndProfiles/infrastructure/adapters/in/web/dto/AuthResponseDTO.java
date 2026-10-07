package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de los inicios de sesión. Cuando la cuenta tiene el segundo factor activado (RF-AUTH-9) no trae tokens:
 * viene con twoFactorRequired=true y un challengeId que se canjea, junto al código enviado por correo, en
 * POST /auth/2fa/verify-login.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponseDTO {
    private String accessToken;
    private String refreshToken;
    private String message;
    private String email;
    private boolean twoFactorRequired;
    private String challengeId;

    /** Inicio de sesión completo: tokens emitidos. */
    public AuthResponseDTO(String accessToken, String refreshToken, String message, String email) {
        this(accessToken, refreshToken, message, email, false, null);
    }

    /** Falta el segundo factor: sin tokens, con el reto que hay que resolver. */
    public static AuthResponseDTO twoFactorChallenge(String email, String challengeId) {
        return new AuthResponseDTO(null, null, "Ingresa el código de verificación que enviamos a tu correo.", email,
                true, challengeId);
    }
}
