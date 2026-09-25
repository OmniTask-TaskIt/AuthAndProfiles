package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.ResolveReportUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.Report;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ResolveReportUseCaseTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ResolveReportUseCase resolveReportUseCase;

    @Test
    void execute_deberiaResolverUnReporteAbierto() {
        Report report = Report.builder().id("report-1").status(ReportStatus.OPEN).build();
        when(reportRepository.findById("report-1")).thenReturn(Optional.of(report));
        when(reportRepository.save(report)).thenReturn(report);

        Report result = resolveReportUseCase.execute("report-1", ReportStatus.DISMISSED, "sin mérito",
                "admin@omnitask.com");

        assertThat(result.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(result.getResolvedBy()).isEqualTo("admin@omnitask.com");
        assertThat(result.getResolutionNote()).isEqualTo("sin mérito");
        assertThat(result.getResolvedAt()).isNotNull();
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElReporteYaFueResuelto() {
        Report report = Report.builder().id("report-1").status(ReportStatus.RESOLVED).build();
        when(reportRepository.findById("report-1")).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> resolveReportUseCase.execute("report-1", ReportStatus.DISMISSED, null, "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya fue resuelto");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElReporteNoExiste() {
        when(reportRepository.findById("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolveReportUseCase.execute("no-existe", ReportStatus.RESOLVED, null, "a"))
                .isInstanceOf(NotFoundException.class);
    }
}
