package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResolveReportRequestDTO {

    @NotNull(message = "El nuevo estado es obligatorio (RESOLVED o DISMISSED)")
    private ReportStatus status;

    @Size(max = 500, message = "La nota no puede superar los 500 caracteres")
    private String note;

    @AssertTrue(message = "El estado debe ser RESOLVED o DISMISSED")
    private boolean isValidTargetStatus() {
        return status == null || status == ReportStatus.RESOLVED || status == ReportStatus.DISMISSED;
    }
}
