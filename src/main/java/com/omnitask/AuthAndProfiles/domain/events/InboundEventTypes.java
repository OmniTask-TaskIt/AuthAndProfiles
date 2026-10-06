package com.omnitask.AuthAndProfiles.domain.events;

/** Valores del campo eventType de los eventos que este MS consume desde taskit.security.events y taskit.task.events. */
public final class InboundEventTypes {

    public static final String IDENTITY_VERIFICATION_RESOLVED = "IdentityVerificationResolved";
    public static final String ACCOUNT_SANCTION_APPLIED = "AccountSanctionApplied";

    /** Entrante desde Task Service (topic taskit.task.events): una tarea fue completada por un prestador. */
    public static final String TASK_COMPLETED = "TaskCompleted";

    private InboundEventTypes() {
    }
}
