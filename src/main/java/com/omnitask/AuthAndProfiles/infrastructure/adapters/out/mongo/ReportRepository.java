package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo;

import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import com.omnitask.AuthAndProfiles.domain.models.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReportRepository extends MongoRepository<Report, String> {

    boolean existsByReporterIdAndRevieweeIdAndStatus(String reporterId, String revieweeId, ReportStatus status);

    long countByRevieweeIdAndStatus(String revieweeId, ReportStatus status);

    Page<Report> findByStatus(ReportStatus status, Pageable pageable);
}
