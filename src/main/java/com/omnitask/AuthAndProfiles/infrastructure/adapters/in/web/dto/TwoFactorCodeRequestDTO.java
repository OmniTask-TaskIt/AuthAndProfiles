package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Código de 6 dígitos enviado por correo para confirmar la activación o desactivación del segundo factor. */
@Data
public class TwoFactorCodeRequestDTO {

    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener exactamente 6 dígitos")
    private String code;
}
