package com.omnitask.AuthAndProfiles.domain.models;

import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "profiles")
public class Profile {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String fullName;
    private String currentRole;

    private String description;
    private String photoUrl;
    private List<String> categories;
    private String locationCoverage;

    private Float reputationScore;
    private int totalReviews;

    /** Obsoleto: antes guardaba la URL pública del documento. Ya no se escribe; ver documentBlobName. */
    private String documentUrl;

    /** Nombre del blob en el contenedor PRIVADO de documentos (nunca una URL). */
    private String documentBlobName;
    private String documentType;
    private String documentContentType;
    private long documentSizeBytes;
    private LocalDateTime documentSubmittedAt;

    private VerificationStatus identityVerificationStatus;
    /** Motivo del rechazo cuando el estado es REJECTED. */
    private String verificationReason;
    private LocalDateTime verificationReviewedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}