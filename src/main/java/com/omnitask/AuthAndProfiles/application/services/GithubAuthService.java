package com.omnitask.AuthAndProfiles.application.services;

import com.omnitask.AuthAndProfiles.domain.exceptions.AuthenticationFailedException;
import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Intercambia el código de autorización de GitHub OAuth por un access token y consulta el perfil del
 * usuario (login, name, email). GitHub no siempre devuelve el correo en /user (puede ser privado), así que
 * si falta se consulta /user/emails y se toma el primario y verificado.
 */
@Slf4j
@Service
public class GithubAuthService {

    @Value("${github.client.id}")
    private String clientId;

    @Value("${github.client.secret}")
    private String clientSecret;

    /** Opcional: solo es obligatorio si la GitHub OAuth App tiene más de una URL de callback registrada. */
    @Value("${github.redirect-uri:}")
    private String redirectUri;

    private RestClient oauthClient = RestClient.builder().baseUrl("https://github.com").build();
    private RestClient apiClient = RestClient.builder().baseUrl("https://api.github.com").build();

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(String access_token, String error, String error_description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubUser(Long id, String login, String name, String email) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubEmail(String email, boolean primary, boolean verified) {
    }

    public String exchangeCodeForAccessToken(String code) {
        TokenResponse response;
        try {
            Map<String, String> body = redirectUri == null || redirectUri.isBlank()
                    ? Map.of("client_id", clientId, "client_secret", clientSecret, "code", code)
                    : Map.of("client_id", clientId, "client_secret", clientSecret, "code", code, "redirect_uri",
                            redirectUri);

            response = oauthClient.post()
                    .uri("/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            log.error("[ERROR] No se pudo contactar a GitHub para intercambiar el código: {}", e.getMessage());
            throw new ExternalServiceException("Error al comunicarse con GitHub: " + e.getMessage());
        }

        if (response == null || response.access_token() == null || response.access_token().isBlank()) {
            String reason = response == null ? "respuesta vacía" : response.error_description();
            log.warn("[SECURITY] GitHub rechazó el intercambio del código de autorización: {}", reason);
            throw new AuthenticationFailedException("Código de autorización de GitHub inválido o expirado");
        }
        return response.access_token();
    }

    public GithubProfile fetchProfile(String accessToken) {
        GithubUser user;
        try {
            user = apiClient.get()
                    .uri("/user")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(GithubUser.class);
        } catch (RestClientException e) {
            log.error("[ERROR] No se pudo obtener el perfil de GitHub: {}", e.getMessage());
            throw new ExternalServiceException("Error al comunicarse con GitHub: " + e.getMessage());
        }

        if (user == null) {
            throw new AuthenticationFailedException("No se pudo obtener el perfil de GitHub");
        }

        String email = user.email();
        if (email == null || email.isBlank()) {
            email = fetchVerifiedPrimaryEmail(accessToken);
        }

        return new GithubProfile(email, user.name(), user.login());
    }

    private String fetchVerifiedPrimaryEmail(String accessToken) {
        List<GithubEmail> emails;
        try {
            emails = apiClient.get()
                    .uri("/user/emails")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<GithubEmail>>() {
                    });
        } catch (RestClientException e) {
            throw new ExternalServiceException("Error al comunicarse con GitHub: " + e.getMessage());
        }

        if (emails == null) {
            emails = List.of();
        }

        return emails.stream()
                .filter(GithubEmail::verified)
                .sorted((a, b) -> Boolean.compare(b.primary(), a.primary()))
                .map(GithubEmail::email)
                .findFirst()
                .orElseThrow(() -> new AuthenticationFailedException(
                        "No se encontró un correo verificado en tu cuenta de GitHub. Verifica un correo en GitHub e intenta de nuevo."));
    }
}
