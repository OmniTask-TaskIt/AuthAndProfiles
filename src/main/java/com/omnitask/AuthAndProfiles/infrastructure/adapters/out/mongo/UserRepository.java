package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    List<User> findByNameContainingIgnoreCase(String name);

    Page<User> findByAccountStatus(AccountStatus accountStatus, Pageable pageable);

    Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    Page<User> findByAccountStatusAndEmailContainingIgnoreCase(AccountStatus accountStatus, String email,
            Pageable pageable);
}
