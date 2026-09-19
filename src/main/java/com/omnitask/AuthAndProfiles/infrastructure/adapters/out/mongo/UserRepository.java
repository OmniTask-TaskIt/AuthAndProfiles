package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo;

import com.omnitask.AuthAndProfiles.domain.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    List<User> findByNameContainingIgnoreCase(String name);
}
