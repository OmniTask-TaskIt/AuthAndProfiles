package com.omnitask.AuthAndProfiles.domain.models;

import com.omnitask.AuthAndProfiles.domain.enums.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Reporte de un usuario contra otro por comportamiento inapropiado o fraude (RF-AUTHPR-9). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reports")
public class Report {

    @Id
    private String id;

    private String reporterId;
    private String revieweeId;
    private String reason;
    private String comment;

    private ReportStatus status;
    private LocalDateTime createdAt;

    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNote;
}
