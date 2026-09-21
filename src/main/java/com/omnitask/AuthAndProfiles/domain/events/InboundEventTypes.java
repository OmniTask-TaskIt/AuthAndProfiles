package com.omnitask.AuthAndProfiles.domain.events;

/** Valores del campo eventType de los eventos que este MS consume desde taskit.security.events. */
public final class InboundEventTypes {

    public static final String IDENTITY_VERIFICATION_RESOLVED = "IdentityVerificationResolved";
    public static final String ACCOUNT_SANCTION_APPLIED = "AccountSanctionApplied";

    private InboundEventTypes() {
    }
}
