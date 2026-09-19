package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOtpRequestDTO {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "El código OTP es obligatorio")
    @Size(min = 6, max = 6, message = "El código OTP debe tener exactamente 6 dígitos")
    private String otpCode;
}
