package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.ListReportsUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.Report;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AdminReportDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReportRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListReportsUseCaseTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ListReportsUseCase listReportsUseCase;

    @Test
    void execute_deberiaUsarOpenComoEstadoPorDefecto() {
        Report report = Report.builder().id("report-1").reporterId("u1").revieweeId("u2")
                .status(ReportStatus.OPEN).build();
        when(reportRepository.findByStatus(eq(ReportStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(userRepository.findAllById(Set.of("u1", "u2"))).thenReturn(List.of());

        PageResponseDTO<AdminReportDTO> result = listReportsUseCase.execute(null, 0, 20);

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

    @Test
    void execute_deberiaEnriquecerCadaReporteConNombreYCorreoDeAmbosUsuarios() {
        Report report = Report.builder().id("report-1").reporterId("u1").revieweeId("u2")
                .reason("Fraude").status(ReportStatus.OPEN).build();
        User reporter = User.builder().id("u1").name("Ana").email("ana@taskit.com").role(Role.SEEKER).build();
        User reviewee = User.builder().id("u2").name("Luis").email("luis@taskit.com").role(Role.PROVIDER).build();

        when(reportRepository.findByStatus(eq(ReportStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(userRepository.findAllById(Set.of("u1", "u2"))).thenReturn(List.of(reporter, reviewee));

        PageResponseDTO<AdminReportDTO> result = listReportsUseCase.execute(ReportStatus.OPEN, 0, 20);

        AdminReportDTO dto = result.content().get(0);
        assertThat(dto.reporterName()).isEqualTo("Ana");
        assertThat(dto.reporterEmail()).isEqualTo("ana@taskit.com");
        assertThat(dto.revieweeName()).isEqualTo("Luis");
        assertThat(dto.revieweeEmail()).isEqualTo("luis@taskit.com");
    }

    @Test
    void execute_noDeberiaFallarSiElUsuarioReportanteOReportadoYaNoExiste() {
        Report report = Report.builder().id("report-1").reporterId("u1").revieweeId("u2")
                .status(ReportStatus.OPEN).build();
        when(reportRepository.findByStatus(eq(ReportStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(userRepository.findAllById(Set.of("u1", "u2"))).thenReturn(List.of());

        PageResponseDTO<AdminReportDTO> result = listReportsUseCase.execute(ReportStatus.OPEN, 0, 20);

        AdminReportDTO dto = result.content().get(0);
        assertThat(dto.reporterName()).isNull();
        assertThat(dto.revieweeName()).isNull();
    }
}
