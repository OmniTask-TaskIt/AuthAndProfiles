package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import com.omnitask.AuthAndProfiles.domain.enums.VerificationDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResolveVerificationRequestDTO {

    @NotNull(message = "La decisión es obligatoria (APPROVED o REJECTED)")
    private VerificationDecision decision;

    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    private String reason;
}
