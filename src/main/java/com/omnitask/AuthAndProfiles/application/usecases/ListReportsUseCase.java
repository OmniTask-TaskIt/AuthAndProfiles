package com.omnitask.AuthAndProfiles.application.usecases;

import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.PageResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.ReportResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Listado paginado de reportes para el panel de administración/HITL. */
@Service
@RequiredArgsConstructor
public class ListReportsUseCase {

    static final int MAX_PAGE_SIZE = 50;

    private final ReportRepository reportRepository;

    public PageResponseDTO<ReportResponseDTO> execute(ReportStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        ReportStatus effectiveStatus = status == null ? ReportStatus.OPEN : status;
        return PageResponseDTO.from(
                reportRepository.findByStatus(effectiveStatus, pageable).map(ReportResponseDTO::fromReport));
    }
}
