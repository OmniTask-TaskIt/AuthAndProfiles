package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.OtpAttemptService;
import com.omnitask.AuthAndProfiles.application.services.OtpGenerator;
import com.omnitask.AuthAndProfiles.application.services.TwoFactorService;
import com.omnitask.AuthAndProfiles.domain.enums.TwoFactorAction;
import com.omnitask.AuthAndProfiles.domain.exceptions.TooManyAttemptsException;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    private static final String EMAIL = "test@gmail.com";

    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private OtpGenerator otpGenerator;
    @Mock
    private OtpAttemptService otpAttemptService;
    @Mock
    private ResendEmailService resendEmailService;

    @InjectMocks
    private TwoFactorService twoFactorService;

    // ------------------------------------------------------------ Ajustes (activar / desactivar)

    @Test
    void sendSettingsCode_deberiaEnviarElCorreoYGuardarElCodigoPorAccion() {
        when(otpGenerator.generate()).thenReturn("123456");

        twoFactorService.sendSettingsCode(EMAIL, TwoFactorAction.ENABLE);

        InOrder order = inOrder(resendEmailService, tokenRedisRepository);
        order.verify(resendEmailService).sendTwoFactorCodeEmail(EMAIL, "123456", 10);
        order.verify(tokenRedisRepository).saveRefreshToken("2fa_code:ENABLE:" + EMAIL, "123456", 600_000L);
    }

    @Test
    void sendSettingsCode_noDeberiaGuardarNada_cuandoFallaElCorreo() {
        // Un código que nadie recibió no debe quedar vigente en Redis
        when(otpGenerator.generate()).thenReturn("123456");
        doThrow(new RuntimeException("Resend caído")).when(resendEmailService)
                .sendTwoFactorCodeEmail(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());

        assertThatThrownBy(() -> twoFactorService.sendSettingsCode(EMAIL, TwoFactorAction.DISABLE))
                .isInstanceOf(RuntimeException.class);
        verify(tokenRedisRepository, never()).saveRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    void verifySettingsCode_deberiaConsumirElCodigoYLimpiarLosIntentos_cuandoEsCorrecto() {
        when(tokenRedisRepository.getRefreshToken("2fa_code:ENABLE:" + EMAIL)).thenReturn("123456");

        assertThatCode(() -> twoFactorService.verifySettingsCode(EMAIL, TwoFactorAction.ENABLE, "123456"))
                .doesNotThrowAnyException();

        verify(tokenRedisRepository).deleteRefreshToken("2fa_code:ENABLE:" + EMAIL);
        verify(otpAttemptService).reset("2fa:ENABLE:" + EMAIL);
    }

    @Test
    void verifySettingsCode_deberiaRechazarUnCodigoIncorrecto_sinConsumirElVigente() {
        when(tokenRedisRepository.getRefreshToken("2fa_code:ENABLE:" + EMAIL)).thenReturn("123456");
        when(otpAttemptService.registerFailureAndCheckLimit("2fa:ENABLE:" + EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> twoFactorService.verifySettingsCode(EMAIL, TwoFactorAction.ENABLE, "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código inválido o expirado");
        verify(tokenRedisRepository, never()).deleteRefreshToken(anyString());
    }

    @Test
    void verifySettingsCode_deberiaRechazar_cuandoNoHayCodigoVigenteONoSeEnvioNinguno() {
        when(tokenRedisRepository.getRefreshToken("2fa_code:DISABLE:" + EMAIL)).thenReturn(null);

        assertThatThrownBy(() -> twoFactorService.verifySettingsCode(EMAIL, TwoFactorAction.DISABLE, "123456"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> twoFactorService.verifySettingsCode(EMAIL, TwoFactorAction.DISABLE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifySettingsCode_deberiaInvalidarElCodigo_alAlcanzarElMaximoDeIntentos() {
        when(tokenRedisRepository.getRefreshToken("2fa_code:ENABLE:" + EMAIL)).thenReturn("123456");
        when(otpAttemptService.registerFailureAndCheckLimit("2fa:ENABLE:" + EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> twoFactorService.verifySettingsCode(EMAIL, TwoFactorAction.ENABLE, "000000"))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(tokenRedisRepository).deleteRefreshToken("2fa_code:ENABLE:" + EMAIL);
    }

    // ------------------------------------------------------------ Reto del inicio de sesión

    @Test
    void startLoginChallenge_deberiaEnviarElCodigoYGuardarElRetoConCincoMinutos() {
        when(otpGenerator.generate()).thenReturn("654321");

        String challengeId = twoFactorService.startLoginChallenge(EMAIL);

        assertThat(challengeId).isNotBlank();
        verify(resendEmailService).sendTwoFactorCodeEmail(EMAIL, "654321", 5);
        verify(tokenRedisRepository).saveRefreshToken("2fa_code:LOGIN:" + challengeId, "654321", 300_000L);
        verify(tokenRedisRepository).saveRefreshToken("2fa_challenge:" + challengeId, EMAIL, 300_000L);
    }

    @Test
    void startLoginChallenge_deberiaGenerarUnRetoDistintoEnCadaInicio() {
        when(otpGenerator.generate()).thenReturn("654321");

        assertThat(twoFactorService.startLoginChallenge(EMAIL)).isNotEqualTo(twoFactorService.startLoginChallenge(EMAIL));
    }

    @Test
    void verifyLoginChallenge_deberiaDevolverElCorreoYCerrarElReto_cuandoElCodigoEsCorrecto() {
        when(tokenRedisRepository.getRefreshToken("2fa_challenge:ch-1")).thenReturn(EMAIL);
        when(tokenRedisRepository.getRefreshToken("2fa_code:LOGIN:ch-1")).thenReturn("654321");

        String email = twoFactorService.verifyLoginChallenge("ch-1", "654321");

        assertThat(email).isEqualTo(EMAIL);
        // el código y el reto quedan consumidos: no se puede reutilizar
        verify(tokenRedisRepository).deleteRefreshToken("2fa_code:LOGIN:ch-1");
        verify(tokenRedisRepository).deleteRefreshToken("2fa_challenge:ch-1");
        verify(otpAttemptService).reset("2fa:login:ch-1");
    }

    @Test
    void verifyLoginChallenge_deberiaRechazar_cuandoElRetoNoExisteOExpiro() {
        when(tokenRedisRepository.getRefreshToken("2fa_challenge:ch-x")).thenReturn(null);

        assertThatThrownBy(() -> twoFactorService.verifyLoginChallenge("ch-x", "654321"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código inválido o expirado");
        verify(otpAttemptService, never()).registerFailureAndCheckLimit(anyString());
    }

    @Test
    void verifyLoginChallenge_deberiaRechazarUnCodigoIncorrecto_ycontarElIntento() {
        when(tokenRedisRepository.getRefreshToken("2fa_challenge:ch-1")).thenReturn(EMAIL);
        when(tokenRedisRepository.getRefreshToken("2fa_code:LOGIN:ch-1")).thenReturn("654321");
        when(otpAttemptService.registerFailureAndCheckLimit("2fa:login:ch-1")).thenReturn(false);

        assertThatThrownBy(() -> twoFactorService.verifyLoginChallenge("ch-1", "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código inválido o expirado");
        // el reto sigue abierto para reintentar con el código correcto
        verify(tokenRedisRepository, never()).deleteRefreshToken("2fa_challenge:ch-1");
    }

    @Test
    void verifyLoginChallenge_deberiaCerrarElReto_alAlcanzarElMaximoDeIntentos() {
        when(tokenRedisRepository.getRefreshToken("2fa_challenge:ch-1")).thenReturn(EMAIL);
        when(tokenRedisRepository.getRefreshToken("2fa_code:LOGIN:ch-1")).thenReturn("654321");
        when(otpAttemptService.registerFailureAndCheckLimit("2fa:login:ch-1")).thenReturn(true);

        assertThatThrownBy(() -> twoFactorService.verifyLoginChallenge("ch-1", "000000"))
                .isInstanceOf(TooManyAttemptsException.class);

        ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
        verify(tokenRedisRepository, org.mockito.Mockito.times(2)).deleteRefreshToken(deleted.capture());
        assertThat(deleted.getAllValues()).containsExactlyInAnyOrder("2fa_code:LOGIN:ch-1", "2fa_challenge:ch-1");
        verify(otpAttemptService, never()).reset(eq("2fa:login:ch-1"));
    }
}
