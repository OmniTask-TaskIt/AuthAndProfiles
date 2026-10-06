package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

/**
 * ResendEmailService crea su RestClient en un campo final. Se reemplaza por reflexión con un
 * RestClient enlazado a MockRestServiceServer, así no se hace ninguna llamada real a Resend.
 */
class ResendEmailServiceTest {

    private ResendEmailService resendEmailService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        resendEmailService = new ResendEmailService();
        ReflectionTestUtils.setField(resendEmailService, "apiKey", "re_test_key");
        ReflectionTestUtils.setField(resendEmailService, "emailFrom", "no-reply@taskit.com");
        ReflectionTestUtils.setField(resendEmailService, "emailEnabled", true);

        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(resendEmailService, "restClient", builder.build());
    }

    @Test
    void sendOtpEmail_deberiaEnviarElCorreoConLosDatosCorrectos_cuandoResendResponde200() {
        // Arrange
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("no-reply@taskit.com"))
                .andExpect(jsonPath("$.to[0]").value("destino@gmail.com"))
                .andExpect(jsonPath("$.html", containsString("123456")))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        // Act & Assert
        assertThatCode(() -> resendEmailService.sendOtpEmail("destino@gmail.com", "123456"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void sendOtpEmail_noDeberiaLlamarAResend_cuandoEmailEnabledEsFalse() {
        // Arrange
        ReflectionTestUtils.setField(resendEmailService, "emailEnabled", false);

        // Act & Assert
        assertThatCode(() -> resendEmailService.sendOtpEmail("destino@gmail.com", "123456"))
                .doesNotThrowAnyException();
        server.verify(); // no se registró ninguna expectativa: pasa solo si no se hizo ninguna petición
    }

    @Test
    void sendOtpEmail_deberiaLanzarRuntimeException_cuandoResendResponde500() {
        // Arrange
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError());

        // Act & Assert
        assertThatThrownBy(() -> resendEmailService.sendOtpEmail("destino@gmail.com", "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error al enviar el correo de verificación");
        server.verify();
    }

    @Test
    void sendPasswordResetEmail_deberiaEnviarElCodigoDeRecuperacion_cuandoResendResponde200() {
        // Arrange
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(jsonPath("$.from").value("no-reply@taskit.com"))
                .andExpect(jsonPath("$.to[0]").value("destino@gmail.com"))
                .andExpect(jsonPath("$.subject", containsString("Recuperación de contraseña")))
                .andExpect(jsonPath("$.html", containsString("654321")))
                .andExpect(jsonPath("$.html", containsString("15 minutos")))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        // Act & Assert
        assertThatCode(() -> resendEmailService.sendPasswordResetEmail("destino@gmail.com", "654321"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void sendPasswordResetEmail_deberiaLanzarExternalServiceException_cuandoResendResponde500() {
        // Arrange
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError());

        // Act & Assert
        assertThatThrownBy(() -> resendEmailService.sendPasswordResetEmail("destino@gmail.com", "654321"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error al enviar el correo de recuperación de contraseña");
        server.verify();
    }

    @Test
    void sendPasswordChangedEmail_deberiaAvisarDelCambioDeContrasena_cuandoResendResponde200() {
        // Arrange
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.to[0]").value("destino@gmail.com"))
                .andExpect(jsonPath("$.subject", containsString("contraseña fue actualizada")))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        // Act & Assert
        assertThatCode(() -> resendEmailService.sendPasswordChangedEmail("destino@gmail.com"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void sendPasswordChangedEmail_deberiaLanzarExternalServiceException_cuandoResendResponde500() {
        // Arrange
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError());

        // Act & Assert
        assertThatThrownBy(() -> resendEmailService.sendPasswordChangedEmail("destino@gmail.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error al enviar la notificación de cambio de contraseña");
        server.verify();
    }

    @Test
    void sendPasswordResetEmailYChanged_noDeberianLlamarAResend_cuandoEmailEnabledEsFalse() {
        // Arrange
        ReflectionTestUtils.setField(resendEmailService, "emailEnabled", false);

        // Act & Assert
        assertThatCode(() -> {
            resendEmailService.sendPasswordResetEmail("destino@gmail.com", "654321");
            resendEmailService.sendPasswordChangedEmail("destino@gmail.com");
        }).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void sendSuspiciousLoginEmail_deberiaAvisarConDispositivoIpYFecha_escapandoElHtml() {
        // Arrange: la IP puede venir de X-Forwarded-For, así que se escapa para no inyectar HTML en el correo
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.to[0]").value("destino@gmail.com"))
                .andExpect(jsonPath("$.subject", containsString("Nuevo inicio de sesión")))
                .andExpect(jsonPath("$.html", containsString("Chrome en Windows")))
                .andExpect(jsonPath("$.html", containsString("&lt;script&gt;")))
                .andExpect(jsonPath("$.html", containsString("05/10/2026 10:30")))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        // Act & Assert
        assertThatCode(() -> resendEmailService.sendSuspiciousLoginEmail("destino@gmail.com", "Chrome en Windows",
                "<script>alert(1)</script>", "05/10/2026 10:30")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void sendSuspiciousLoginEmail_deberiaLanzarExternalServiceException_cuandoResendResponde500() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> resendEmailService.sendSuspiciousLoginEmail("destino@gmail.com", "Chrome en Windows",
                null, "05/10/2026 10:30"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error al enviar el aviso de inicio de sesión sospechoso");
        server.verify();
    }

    @Test
    void sendSuspiciousLoginEmail_noDeberiaLlamarAResend_cuandoEmailEnabledEsFalse() {
        ReflectionTestUtils.setField(resendEmailService, "emailEnabled", false);

        assertThatCode(() -> resendEmailService.sendSuspiciousLoginEmail("destino@gmail.com", "Chrome en Windows",
                "203.0.113.5", "05/10/2026 10:30")).doesNotThrowAnyException();
        server.verify();
    }

    // ------------------------------------------------------------ Reintentos (RNF-AUTHPR-3)

    private void habilitarReintentos() {
        ReflectionTestUtils.setField(resendEmailService, "maxAttempts", 3);
        ReflectionTestUtils.setField(resendEmailService, "retryBackoffMs", 0L);
    }

    @Test
    void send_deberiaReintentarYTerminarBien_cuandoResendFallaConUn500Temporal() {
        // Arrange: falla una vez y la segunda petición funciona; las dos llevan la misma Idempotency-Key
        habilitarReintentos();
        List<String> keys = new ArrayList<>();
        server.expect(ExpectedCount.once(), requestTo("https://api.resend.com/emails"))
                .andExpect(request -> keys.add(request.getHeaders().getFirst("Idempotency-Key")))
                .andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo("https://api.resend.com/emails"))
                .andExpect(request -> keys.add(request.getHeaders().getFirst("Idempotency-Key")))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        // Act & Assert
        assertThatCode(() -> resendEmailService.sendOtpEmail("destino@gmail.com", "123456"))
                .doesNotThrowAnyException();
        server.verify();
        assertThat(keys).hasSize(2).doesNotContainNull();
        assertThat(keys.get(0)).isEqualTo(keys.get(1));
    }

    @Test
    void send_deberiaReintentar_cuandoResendResponde429() {
        habilitarReintentos();
        server.expect(ExpectedCount.once(), requestTo("https://api.resend.com/emails"))
                .andRespond(withTooManyRequests());
        server.expect(ExpectedCount.once(), requestTo("https://api.resend.com/emails"))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> resendEmailService.sendOtpEmail("destino@gmail.com", "123456"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void send_deberiaReintentar_cuandoFallaLaRed() {
        habilitarReintentos();
        server.expect(ExpectedCount.once(), requestTo("https://api.resend.com/emails"))
                .andRespond(withException(new IOException("Connection reset")));
        server.expect(ExpectedCount.once(), requestTo("https://api.resend.com/emails"))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> resendEmailService.sendOtpEmail("destino@gmail.com", "123456"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void send_deberiaRendirseTrasLosIntentosMaximos_yLanzarExternalServiceException() {
        habilitarReintentos();
        server.expect(ExpectedCount.times(3), requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> resendEmailService.sendOtpEmail("destino@gmail.com", "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error al enviar el correo de verificación");
        server.verify();
    }

    @Test
    void send_noDeberiaReintentar_cuandoElErrorEsDelCliente() {
        // Un 400 o un 401 (clave inválida) no se arreglan reintentando: una sola petición
        habilitarReintentos();
        server.expect(ExpectedCount.once(), requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> resendEmailService.sendOtpEmail("destino@gmail.com", "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error al enviar el correo de verificación");
        server.verify();
    }

    @Test
    void send_deberiaDejarDeReintentar_cuandoElHiloEsInterrumpidoDuranteLaEspera() {
        // Arrange: tras el primer fallo, la espera detecta la interrupción y no se hace un segundo intento
        ReflectionTestUtils.setField(resendEmailService, "maxAttempts", 3);
        ReflectionTestUtils.setField(resendEmailService, "retryBackoffMs", 1L);
        server.expect(ExpectedCount.once(), requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError());
        Thread.currentThread().interrupt();

        try {
            // Act & Assert
            assertThatThrownBy(() -> resendEmailService.sendOtpEmail("destino@gmail.com", "123456"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Error al enviar el correo de verificación");
            // el estado de interrupción se restaura para quien llamó
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            server.verify();
        } finally {
            Thread.interrupted(); // limpia la marca para no afectar a otros tests
        }
    }
}
