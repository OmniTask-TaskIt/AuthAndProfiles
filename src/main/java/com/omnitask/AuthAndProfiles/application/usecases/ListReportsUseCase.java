package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import com.omnitask.AuthAndProfiles.domain.models.Report;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AdminReportDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReportRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Listado paginado de reportes para el panel de administración/HITL.
 * Enriquece cada reporte con el nombre y correo del reportante y del
 * reportado (una sola consulta batch a UserRepository, no N+1) para que el
 * panel no tenga que mostrar IDs de Mongo sin contexto.
 */
@Service
@RequiredArgsConstructor
public class ListReportsUseCase {

    static final int MAX_PAGE_SIZE = 50;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public PageResponseDTO<AdminReportDTO> execute(ReportStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        ReportStatus effectiveStatus = status == null ? ReportStatus.OPEN : status;
        Page<Report> reportsPage = reportRepository.findByStatus(effectiveStatus, pageable);

        Set<String> userIds = reportsPage.getContent().stream()
                .flatMap(r -> Stream.of(r.getReporterId(), r.getRevieweeId()))
                .collect(Collectors.toSet());
        Map<String, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return PageResponseDTO.from(reportsPage
                .map(r -> AdminReportDTO.from(r, usersById.get(r.getReporterId()), usersById.get(r.getRevieweeId()))));
    }
}
