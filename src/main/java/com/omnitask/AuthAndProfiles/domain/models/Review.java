package com.omnitask.AuthAndProfiles.domain.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Calificación que un usuario deja a otro tras completar una tarea juntos (RF-AUTHPR-5).
 * Un mismo usuario solo puede calificar una misma tarea una vez: índice único (taskId, reviewerId).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reviews")
@CompoundIndex(name = "task_reviewer_unique", def = "{'taskId': 1, 'reviewerId': 1}", unique = true)
public class Review {

    @Id
    private String id;

    private String taskId;
    private String reviewerId;
    private String revieweeId;

    /** 1 a 5. */
    private int rating;
    private String comment;

    private LocalDateTime createdAt;
}
