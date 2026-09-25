package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateReportRequestDTO {

    @NotBlank(message = "El motivo del reporte es obligatorio")
    @Size(max = 200, message = "El motivo no puede superar los 200 caracteres")
    private String reason;

    @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres")
    private String comment;
}
