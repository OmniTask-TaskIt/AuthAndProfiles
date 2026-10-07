package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.TwoFactorService;
import com.omnitask.AuthAndProfiles.application.usecases.TwoFactorSettingsUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.TwoFactorAction;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.SecurityAuditEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoFactorSettingsUseCaseTest {

    private static final String EMAIL = "test@gmail.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private TwoFactorService twoFactorService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private TwoFactorSettingsUseCase useCase;

    private User user(boolean twoFactorEnabled) {
        User user = User.builder().email(EMAIL).twoFactorEnabled(twoFactorEnabled).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void isEnabled_deberiaReflejarElEstadoDeLaCuenta() {
        user(true);

        assertThat(useCase.isEnabled(EMAIL)).isTrue();
    }

    @Test
    void isEnabled_deberiaLanzarNotFound_cuandoElUsuarioNoExiste() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.isEnabled(EMAIL)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void requestCode_deberiaEnviarElCodigoDeActivacion_cuandoEstaDesactivado() {
        user(false);

        useCase.requestCode(EMAIL, TwoFactorAction.ENABLE);

        verify(twoFactorService).sendSettingsCode(EMAIL, TwoFactorAction.ENABLE);
    }

    @Test
    void requestCode_deberiaEnviarElCodigoDeDesactivacion_cuandoEstaActivado() {
        user(true);

        useCase.requestCode(EMAIL, TwoFactorAction.DISABLE);

        verify(twoFactorService).sendSettingsCode(EMAIL, TwoFactorAction.DISABLE);
    }

    @Test
    void requestCode_deberiaRechazarActivar_cuandoYaEstaActivado() {
        user(true);

        assertThatThrownBy(() -> useCase.requestCode(EMAIL, TwoFactorAction.ENABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La verificación en dos pasos ya está activada.");
        verify(twoFactorService, never()).sendSettingsCode(any(), any());
    }

    @Test
    void requestCode_deberiaRechazarDesactivar_cuandoNoEstaActivado() {
        user(false);

        assertThatThrownBy(() -> useCase.requestCode(EMAIL, TwoFactorAction.DISABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La verificación en dos pasos no está activada.");
        verify(twoFactorService, never()).sendSettingsCode(any(), any());
    }

    @Test
    void confirm_deberiaActivarElSegundoFactorYAuditar_conElCodigoCorrecto() {
        User user = user(false);

        useCase.confirm(EMAIL, "123456", TwoFactorAction.ENABLE);

        verify(twoFactorService).verifySettingsCode(EMAIL, TwoFactorAction.ENABLE, "123456");
        assertThat(user.isTwoFactorEnabled()).isTrue();
        assertThat(user.getUpdatedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq(EMAIL),
                argThat((SecurityAuditEvent e) -> e.action().equals("2FA_ENABLED")));
    }

    @Test
    void confirm_deberiaDesactivarElSegundoFactorYAuditar_conElCodigoCorrecto() {
        User user = user(true);

        useCase.confirm(EMAIL, "123456", TwoFactorAction.DISABLE);

        assertThat(user.isTwoFactorEnabled()).isFalse();
        verify(userRepository).save(user);
        verify(eventPublisher).publish(eq(EventType.SECURITY_AUDIT), eq(EMAIL),
                argThat((SecurityAuditEvent e) -> e.action().equals("2FA_DISABLED")));
    }

    @Test
    void confirm_noDeberiaCambiarNada_cuandoElCodigoEsIncorrecto() {
        User user = user(false);
        doThrow(new IllegalArgumentException("Código inválido o expirado")).when(twoFactorService)
                .verifySettingsCode(EMAIL, TwoFactorAction.ENABLE, "000000");

        assertThatThrownBy(() -> useCase.confirm(EMAIL, "000000", TwoFactorAction.ENABLE))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.isTwoFactorEnabled()).isFalse();
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void confirm_deberiaRechazarElCambio_cuandoYaEstaEnEseEstado() {
        user(true);

        assertThatThrownBy(() -> useCase.confirm(EMAIL, "123456", TwoFactorAction.ENABLE))
                .isInstanceOf(IllegalArgumentException.class);
        verify(twoFactorService, never()).verifySettingsCode(any(), any(), any());
    }
}
