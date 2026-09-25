package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto;

import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import com.omnitask.AuthAndProfiles.domain.models.Report;

import java.time.LocalDateTime;

public record ReportResponseDTO(
        String id,
        String reporterId,
        String revieweeId,
        String reason,
        String comment,
        ReportStatus status,
        LocalDateTime createdAt,
        String resolvedBy,
        LocalDateTime resolvedAt,
        String resolutionNote) {

    public static ReportResponseDTO fromReport(Report report) {
        return new ReportResponseDTO(report.getId(), report.getReporterId(), report.getRevieweeId(),
                report.getReason(), report.getComment(), report.getStatus(), report.getCreatedAt(),
                report.getResolvedBy(), report.getResolvedAt(), report.getResolutionNote());
    }
}
