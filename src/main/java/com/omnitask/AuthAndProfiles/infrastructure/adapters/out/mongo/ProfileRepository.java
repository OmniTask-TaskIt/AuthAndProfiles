package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo;

import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileRepository extends MongoRepository<Profile, String> {
    Optional<Profile> findByUserId(String userId);

    List<Profile> findByUserIdIn(List<String> userIds);

    Page<Profile> findByIdentityVerificationStatus(VerificationStatus status, Pageable pageable);

    /**
     * Suma una tarea completada al perfil del usuario de forma atómica ($inc en Mongo): dos eventos simultáneos
     * no pisan el contador. Devuelve cuántos perfiles se modificaron (0 si el usuario no tiene perfil).
     */
    @Query("{ 'userId': ?0 }")
    @Update("{ '$inc': { 'tasksCompleted': 1 } }")
    long incrementTasksCompletedByUserId(String userId);
}
