package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.omnitask.AuthAndProfiles.domain.enums.Role;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRequestDTO {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@(gmail\\.com|hotmail\\.com|yahoo\\.com|.*\\.edu|.*\\.edu\\.[a-z]{2}|[A-Za-z0-9.-]+\\.[a-z]{2,})$", message = "El correo debe ser de dominio Gmail, Hotmail, Yahoo o un correo institucional/corporativo válido.")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$", message = "La contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un carácter especial.")
    private String password;

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    @NotNull(message = "El rol es obligatorio (SEEKER o PROVIDER)")
    private Role role;

    @AssertTrue(message = "Debe aceptar los Términos y Condiciones y la Política de Privacidad")
    private boolean acceptedTerms;
}
