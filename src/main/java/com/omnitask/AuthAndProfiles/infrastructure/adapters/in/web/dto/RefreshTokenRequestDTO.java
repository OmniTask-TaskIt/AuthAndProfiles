package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequestDTO {
    @NotBlank(message = "El email es obligatorio")
    private String email;

    @NotBlank(message = "El refresh token es obligatorio")
    private String refreshToken;
}
