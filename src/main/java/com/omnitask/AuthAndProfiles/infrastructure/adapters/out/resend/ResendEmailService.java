package com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend;

import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.UUID;

/**
 * Cliente de Resend. RNF-AUTHPR-3: ante fallos temporales del proveedor (red caída, timeout, 5xx o 429) el envío se
 * reintenta con espera exponencial (app.email.retry-backoff-ms, 2x en cada intento) hasta app.email.max-attempts
 * intentos. Los errores del cliente (4xx: clave inválida, correo mal formado) no se reintentan porque no se arreglan
 * solos. Todos los intentos de un mismo correo llevan la misma Idempotency-Key, así Resend no lo entrega dos veces si
 * la primera petición sí llegó pero la respuesta se perdió.
 */
@Slf4j
@Service
public class ResendEmailService {

    @Value("${resend.api.key}")
    private String apiKey;

    @Value("${resend.email.from}")
    private String emailFrom;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${app.email.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.email.retry-backoff-ms:300}")
    private long retryBackoffMs;

    private final RestClient restClient = buildRestClient();

    /** Con timeouts: sin ellos, una red caída bloquearía la petición y los reintentos nunca llegarían a ejecutarse. */
    private static RestClient buildRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);
        return RestClient.builder()
                .baseUrl("https://api.resend.com")
                .requestFactory(requestFactory)
                .build();
    }

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

    /** Aviso de inicio de sesión desde un dispositivo no reconocido (RF-AUTH-11). */
    public void sendSuspiciousLoginEmail(String toEmail, String deviceInfo, String ipAddress, String when) {
        // La IP puede venir de la cabecera X-Forwarded-For (controlada por quien inicia sesión): se escapa.
        String html = "<div style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Nuevo inicio de sesión en tu cuenta</h2>" +
                "<p>Detectamos un inicio de sesión desde un dispositivo que no habíamos visto antes:</p>" +
                "<ul>" +
                "<li><b>Dispositivo:</b> " + HtmlUtils.htmlEscape(deviceInfo) + "</li>" +
                "<li><b>Dirección IP:</b> " + HtmlUtils.htmlEscape(String.valueOf(ipAddress)) + "</li>" +
                "<li><b>Fecha:</b> " + HtmlUtils.htmlEscape(when) + "</li>" +
                "</ul>" +
                "<p>Si fuiste tú, no tienes que hacer nada. Si no, cambia tu contraseña de inmediato con la opción " +
                "\"¿Olvidaste tu contraseña?\" y cierra las sesiones que no reconozcas desde la sección de seguridad.</p>" +
                "</div>";

        send(toEmail, "Nuevo inicio de sesión - TaskIt Platform", html,
                "inicio de sesión sospechoso", "Error al enviar el aviso de inicio de sesión sospechoso");
    }

    /** Código del segundo factor (RF-AUTH-9): activar, desactivar o completar un inicio de sesión. */
    public void sendTwoFactorCodeEmail(String toEmail, String code, int expiresInMinutes) {
        String html = "<div style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Verificación en dos pasos</h2>" +
                "<p>Tu código de verificación es:</p>" +
                "<h1 style='color: #4F46E5; letter-spacing: 2px;'>" + code + "</h1>" +
                "<p>Este código expirará en " + expiresInMinutes + " minutos.</p>" +
                "<p>Si no fuiste tú, ignora este mensaje y cambia tu contraseña.</p>" +
                "</div>";

        send(toEmail, "Código de verificación en dos pasos - TaskIt Platform", html,
                "verificación en dos pasos", "Error al enviar el código de verificación en dos pasos");
    }

    private void send(String toEmail, String subject, String html, String label, String failureMessage) {
        if (!emailEnabled) {
            log.info("[SEC-AUTH] [RESEND] Envío de correo deshabilitado (app.email.enabled=false); correo de {} para {} no enviado (el código, si aplica, queda disponible en Redis)",
                    label, toEmail);
            return;
        }
        Map<String, Object> requestBody = Map.of(
                "from", emailFrom,
                "to", new String[] { toEmail },
                "subject", subject,
                "html", html);
        String idempotencyKey = UUID.randomUUID().toString();

        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                restClient.post()
                        .uri("/emails")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .toBodilessEntity();

                log.info("[SEC-AUTH] [RESEND] Correo de {} enviado exitosamente a: {}", label, toEmail);
                return;

            } catch (Exception e) {
                if (attempt == attempts || !isTransient(e)) {
                    log.error("[ERROR] No se pudo enviar el correo a través de Resend (intento {}/{}): {}", attempt,
                            attempts, e.getMessage());
                    throw new ExternalServiceException(failureMessage);
                }
                log.warn("[RESEND] Fallo temporal al enviar el correo de {} (intento {}/{}): {}. Se reintenta.", label,
                        attempt, attempts, e.getMessage());
                if (!pauseBeforeRetry(attempt)) {
                    throw new ExternalServiceException(failureMessage);
                }
            }
        }
    }

    /** Fallos que suelen resolverse solos: red o timeout, errores 5xx del proveedor y límite de uso (429). */
    private static boolean isTransient(Exception e) {
        return e instanceof ResourceAccessException
                || e instanceof HttpServerErrorException
                || e instanceof HttpClientErrorException.TooManyRequests;
    }

    /** Espera exponencial: backoff, 2x backoff, 4x backoff... Devuelve false si el hilo fue interrumpido. */
    private boolean pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(Math.max(0, retryBackoffMs) * (1L << (attempt - 1)));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
