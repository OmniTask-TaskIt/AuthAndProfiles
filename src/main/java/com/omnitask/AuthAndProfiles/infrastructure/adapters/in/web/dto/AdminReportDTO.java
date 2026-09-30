package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import com.omnitask.AuthAndProfiles.domain.models.Report;
import com.omnitask.AuthAndProfiles.domain.models.User;

import java.time.LocalDateTime;

/**
 * Igual que {@link ReportResponseDTO}, pero con el nombre y correo del
 * reportante y del reportado ya resueltos, para que el panel de
 * administración no tenga que mostrar IDs de Mongo. Si alguno de los dos
 * usuarios ya no existe, esos campos quedan en null (el reporte se sigue
 * mostrando igual, solo que sin ese dato).
 */
public record AdminReportDTO(
        String id,
        String reporterId,
        String reporterName,
        String reporterEmail,
        String revieweeId,
        String revieweeName,
        String revieweeEmail,
        String reason,
        String comment,
        ReportStatus status,
        LocalDateTime createdAt,
        String resolvedBy,
        LocalDateTime resolvedAt,
        String resolutionNote) {

    public static AdminReportDTO from(Report report, User reporter, User reviewee) {
        return new AdminReportDTO(
                report.getId(),
                report.getReporterId(),
                reporter != null ? reporter.getName() : null,
                reporter != null ? reporter.getEmail() : null,
                report.getRevieweeId(),
                reviewee != null ? reviewee.getName() : null,
                reviewee != null ? reviewee.getEmail() : null,
                report.getReason(),
                report.getComment(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getResolvedBy(),
                report.getResolvedAt(),
                report.getResolutionNote());
    }
}
