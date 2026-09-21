package com.omnitask.AuthAndProfiles.domain.policies;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.exceptions.AccountRestrictedException;
import com.omnitask.AuthAndProfiles.domain.models.User;

/**
 * Regla única para decidir si una cuenta puede autenticarse (login, Google, refresh).
 * Una cuenta SUSPENDED o BLOCKED no puede obtener ni renovar tokens.
 */
public final class AccountAccessPolicy {

    private AccountAccessPolicy() {
    }

    public static void ensureNotRestricted(User user) {
        AccountStatus status = user.getAccountStatus();
        if (status == null || !status.isRestricted()) {
            return;
        }
        if (status == AccountStatus.BLOCKED) {
            throw new AccountRestrictedException("Tu cuenta ha sido bloqueada. Contacta al equipo de soporte de Task It.");
        }
        throw new AccountRestrictedException("Tu cuenta está suspendida. Contacta al equipo de soporte de Task It.");
    }
}
