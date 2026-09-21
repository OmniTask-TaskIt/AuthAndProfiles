package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAccountStatusRequestDTO {

    @NotNull(message = "El nuevo estado es obligatorio (ACTIVE, SUSPENDED o BLOCKED)")
    private AccountStatus status;

    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    private String reason;
}
