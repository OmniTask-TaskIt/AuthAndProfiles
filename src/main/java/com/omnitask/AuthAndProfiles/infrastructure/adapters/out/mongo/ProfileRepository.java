package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo;

import com.omnitask.AuthAndProfiles.domain.models.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileRepository extends MongoRepository<Profile, String> {
    Optional<Profile> findByUserId(String userId);

    List<Profile> findByUserIdIn(List<String> userIds);
}
