package com.omnitask.AuthAndProfiles.domain.models;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String email;
    private String passwordHash;
    private String name;

    private Role role;
    private AuthProvider authProvider;
    private String externalProviderId;

    private boolean emailVerified;
    private boolean termsAccepted;
    private LocalDateTime termsAcceptedAt;
    private String termsVersion;
    private AccountStatus accountStatus;

    private int failedLoginAttempts;
    private String blockReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}
