package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend;

import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
public class ResendEmailService {

    @Value("${resend.api.key}")
    private String apiKey;

    @Value("${resend.email.from}")
    private String emailFrom;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://api.resend.com")
            .build();

    /** Código OTP de verificación de cuenta (RF-AUTH-2). */
    public void sendOtpEmail(String toEmail, String otpCode) {
        String html = "<div style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>¡Bienvenido a TaskIt!</h2>" +
                "<p>Tu código de verificación OTP es:</p>" +
                "<h1 style='color: #4F46E5; letter-spacing: 2px;'>" + otpCode + "</h1>" +
                "<p>Este código expirará en 10 minutos.</p>" +
                "</div>";

        send(toEmail, "Código de Verificación - TaskIt Platform", html,
                "OTP", "Error al enviar el correo de verificación");
    }

    /** Código temporal para restablecer la contraseña (RF-AUTH-4). Vence en 15 minutos (PN-AUTHPR-4). */
    public void sendPasswordResetEmail(String toEmail, String code) {
        String html = "<div style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Recupera tu contraseña de TaskIt</h2>" +
                "<p>Recibimos una solicitud para restablecer tu contraseña. Tu código de recuperación es:</p>" +
                "<h1 style='color: #4F46E5; letter-spacing: 2px;'>" + code + "</h1>" +
                "<p>Este código expirará en 15 minutos y solo puede usarse una vez.</p>" +
                "<p>Si no fuiste tú, ignora este correo: tu contraseña actual sigue siendo la misma.</p>" +
                "</div>";

        send(toEmail, "Recuperación de contraseña - TaskIt Platform", html,
                "recuperación de contraseña", "Error al enviar el correo de recuperación de contraseña");
    }

    /** Aviso de que la contraseña fue modificada (RF-AUTH-14). */
    public void sendPasswordChangedEmail(String toEmail) {
        String html = "<div style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Tu contraseña fue actualizada</h2>" +
                "<p>Te confirmamos que la contraseña de tu cuenta de TaskIt acaba de cambiar y que cerramos tus sesiones abiertas.</p>" +
                "<p>Si no fuiste tú, restablece tu contraseña de inmediato desde la opción " +
                "\"¿Olvidaste tu contraseña?\" y contacta al equipo de soporte.</p>" +
                "</div>";

        send(toEmail, "Tu contraseña fue actualizada - TaskIt Platform", html,
                "cambio de contraseña", "Error al enviar la notificación de cambio de contraseña");
    }

    private void send(String toEmail, String subject, String html, String label, String failureMessage) {
        if (!emailEnabled) {
            log.info("[SEC-AUTH] [RESEND] Envío de correo deshabilitado (app.email.enabled=false); correo de {} para {} no enviado (el código, si aplica, queda disponible en Redis)",
                    label, toEmail);
            return;
        }
        try {
            Map<String, Object> requestBody = Map.of(
                    "from", emailFrom,
                    "to", new String[] { toEmail },
                    "subject", subject,
                    "html", html);

            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[SEC-AUTH] [RESEND] Correo de {} enviado exitosamente a: {}", label, toEmail);

        } catch (Exception e) {
            log.error("[ERROR] No se pudo enviar el correo a través de Resend: {}", e.getMessage());
            throw new ExternalServiceException(failureMessage);
        }
    }
}