package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
}