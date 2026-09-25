package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Report;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Resolución manual de un reporte por un administrador, mientras Security and Audit HITL no esté integrado. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveReportUseCase {

    private final ReportRepository reportRepository;

    public Report execute(String reportId, ReportStatus newStatus, String note, String resolvedBy) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Reporte no encontrado"));

        if (report.getStatus() != ReportStatus.OPEN) {
            throw new IllegalArgumentException("Este reporte ya fue resuelto.");
        }

        report.setStatus(newStatus);
        report.setResolvedBy(resolvedBy);
        report.setResolvedAt(LocalDateTime.now());
        report.setResolutionNote(note);
        Report saved = reportRepository.save(report);

        log.warn("[AUDIT-SECURITY] Reporte {} resuelto como {} por {}", reportId, newStatus, resolvedBy);
        return saved;
    }
}
