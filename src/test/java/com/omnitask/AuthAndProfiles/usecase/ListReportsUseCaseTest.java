package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.ListReportsUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import com.omnitask.AuthAndProfiles.domain.models.Report;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ReportResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListReportsUseCaseTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ListReportsUseCase listReportsUseCase;

    @Test
    void execute_deberiaUsarOpenComoEstadoPorDefecto() {
        Report report = Report.builder().id("report-1").status(ReportStatus.OPEN).build();
        when(reportRepository.findByStatus(eq(ReportStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report)));

        PageResponseDTO<ReportResponseDTO> result = listReportsUseCase.execute(null, 0, 20);

        assertThat(result.content()).hasSize(1);
        verify(reportRepository).findByStatus(eq(ReportStatus.OPEN), any(Pageable.class));
    }

    @Test
    void execute_deberiaFiltrarPorElEstadoSolicitado() {
        when(reportRepository.findByStatus(eq(ReportStatus.RESOLVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        listReportsUseCase.execute(ReportStatus.RESOLVED, 0, 20);

        verify(reportRepository).findByStatus(eq(ReportStatus.RESOLVED), any(Pageable.class));
    }
}
