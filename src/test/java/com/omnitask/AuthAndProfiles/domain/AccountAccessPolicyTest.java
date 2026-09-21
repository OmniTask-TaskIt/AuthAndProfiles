package com.omnitask.AuthAndProfiles.domain;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.exceptions.AccountRestrictedException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.policies.AccountAccessPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountAccessPolicyTest {

    @Test
    void ensureNotRestricted_noDeberiaLanzarExcepcion_cuandoLaCuentaEstaActivaPendienteOSinEstado() {
        assertThatCode(() -> {
            AccountAccessPolicy.ensureNotRestricted(User.builder().accountStatus(AccountStatus.ACTIVE).build());
            AccountAccessPolicy.ensureNotRestricted(
                    User.builder().accountStatus(AccountStatus.PENDING_VERIFICATION).build());
            AccountAccessPolicy.ensureNotRestricted(User.builder().accountStatus(null).build());
        }).doesNotThrowAnyException();
    }

    @Test
    void ensureNotRestricted_deberiaLanzarExcepcion_cuandoLaCuentaEstaSuspendida() {
        User user = User.builder().accountStatus(AccountStatus.SUSPENDED).build();

        assertThatThrownBy(() -> AccountAccessPolicy.ensureNotRestricted(user))
                .isInstanceOf(AccountRestrictedException.class)
                .hasMessageContaining("suspendida");
    }

    @Test
    void ensureNotRestricted_deberiaLanzarExcepcion_cuandoLaCuentaEstaBloqueada() {
        User user = User.builder().accountStatus(AccountStatus.BLOCKED).build();

        assertThatThrownBy(() -> AccountAccessPolicy.ensureNotRestricted(user))
                .isInstanceOf(AccountRestrictedException.class)
                .hasMessageContaining("bloqueada");
    }
}
