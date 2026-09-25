package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.UserReportedEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.ConflictException;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Report;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReportRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Registra el reporte de un usuario contra otro (RF-AUTHPR-9) y, si con este reporte el perfil reportado
 * alcanza el umbral configurado de reportes abiertos, lo suspende preventivamente (PN-AUTHPR-12) hasta que
 * Security and Audit lo revise.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateReportUseCase {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final EventPublisher eventPublisher;
    private final ChangeAccountStatusUseCase changeAccountStatusUseCase;

    @Value("${app.reports.auto-suspend-threshold:3}")
    private int autoSuspendThreshold;

    public Report execute(String reporterEmail, String revieweeId, String reason, String comment) {
        User reporter = userRepository.findByEmail(reporterEmail)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        if (reporter.getId().equals(revieweeId)) {
            throw new IllegalArgumentException("No puedes reportarte a ti mismo.");
        }
        User reviewee = userRepository.findById(revieweeId)
                .orElseThrow(() -> new NotFoundException("El usuario reportado no existe."));
        if (reportRepository.existsByReporterIdAndRevieweeIdAndStatus(reporter.getId(), revieweeId,
                ReportStatus.OPEN)) {
            throw new ConflictException("Ya tienes un reporte abierto contra este usuario.");
        }

        Report report = reportRepository.save(Report.builder()
                .reporterId(reporter.getId())
                .revieweeId(revieweeId)
                .reason(reason)
                .comment(comment)
                .status(ReportStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());

        eventPublisher.publish(EventType.USER_REPORTED, revieweeId,
                new UserReportedEvent(report.getId(), reporter.getId(), revieweeId, reason, comment, Instant.now()));

        log.warn("[AUDIT-SECURITY] Reporte {} registrado: {} reportó a {} por: {}", report.getId(), reporter.getId(),
                revieweeId, reason);

        maybeAutoSuspend(reviewee);
        return report;
    }

    private void maybeAutoSuspend(User reviewee) {
        if (reviewee.getAccountStatus() != null && reviewee.getAccountStatus().isRestricted()) {
            return;
        }
        long openReports = reportRepository.countByRevieweeIdAndStatus(reviewee.getId(), ReportStatus.OPEN);
        if (openReports < autoSuspendThreshold) {
            return;
        }

        changeAccountStatusUseCase.execute(reviewee.getId(), AccountStatus.SUSPENDED,
                "Suspensión preventiva automática: se acumularon " + openReports + " reportes sin resolver.",
                "system:auto-report-threshold");
    }
}
