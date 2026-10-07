package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Segundo paso del inicio de sesión: el reto recibido en el login y el código enviado por correo. */
@Data
public class TwoFactorLoginRequestDTO {

    @NotBlank(message = "El identificador del reto es obligatorio")
    private String challengeId;

    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener exactamente 6 dígitos")
    private String code;
}
