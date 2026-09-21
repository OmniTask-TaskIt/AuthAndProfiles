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

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://api.resend.com")
            .build();

    public void sendOtpEmail(String toEmail, String otpCode) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "from", emailFrom,
                    "to", new String[] { toEmail },
                    "subject", "Código de Verificación - TaskIt Platform",
                    "html", "<div style='font-family: Arial, sans-serif; padding: 20px;'>" +
                            "<h2>¡Bienvenido a TaskIt!</h2>" +
                            "<p>Tu código de verificación OTP es:</p>" +
                            "<h1 style='color: #4F46E5; letter-spacing: 2px;'>" + otpCode + "</h1>" +
                            "<p>Este código expirará en 10 minutos.</p>" +
                            "</div>");

            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[SEC-AUTH] [RESEND] Correo OTP enviado exitosamente a: {}", toEmail);

        } catch (Exception e) {
            log.error("[ERROR] No se pudo enviar el correo a través de Resend: {}", e.getMessage());
            throw new ExternalServiceException("Error al enviar el correo de verificación");
        }
    }
}