package com.omnitask.AuthAndProfiles.domain.enums;

public enum AccountStatus {
    ACTIVE,
    PENDING_VERIFICATION,
    BLOCKED,
    SUSPENDED;

    /** Estados en los que la cuenta no puede iniciar sesión ni renovar tokens. */
    public boolean isRestricted() {
        return this == BLOCKED || this == SUSPENDED;
    }
}
