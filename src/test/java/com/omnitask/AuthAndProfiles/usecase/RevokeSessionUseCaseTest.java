package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.application.usecases.RevokeSessionUseCase;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevokeSessionUseCaseTest {

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private RevokeSessionUseCase revokeSessionUseCase;

    @Test
    void execute_deberiaCerrarLaSesionSolicitada() {
        when(sessionService.revoke("test@gmail.com", "sesion-2", "REMOTE_LOGOUT")).thenReturn(true);

        assertThatCode(() -> revokeSessionUseCase.execute("test@gmail.com", "sesion-2")).doesNotThrowAnyException();
        verify(sessionService).revoke("test@gmail.com", "sesion-2", "REMOTE_LOGOUT");
    }

    @Test
    void execute_deberiaLanzarNotFound_cuandoLaSesionNoExisteONoEsDelUsuario() {
        when(sessionService.revoke("test@gmail.com", "ajena", "REMOTE_LOGOUT")).thenReturn(false);

        assertThatThrownBy(() -> revokeSessionUseCase.execute("test@gmail.com", "ajena"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Sesión no encontrada");
    }
}
