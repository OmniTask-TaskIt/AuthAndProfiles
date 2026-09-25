package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.GithubAuthService;
import com.omnitask.AuthAndProfiles.application.services.GithubProfile;
import com.omnitask.AuthAndProfiles.domain.exceptions.AuthenticationFailedException;
import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * GithubAuthService construye sus dos RestClient en campos de instancia (no final), así que se reemplazan
 * por reflexión con clientes enlazados a MockRestServiceServer; no se hace ninguna llamada real a GitHub.
 */
class GithubAuthServiceTest {

    private GithubAuthService githubAuthService;
    private MockRestServiceServer oauthServer;
    private MockRestServiceServer apiServer;

    @BeforeEach
    void setUp() {
        githubAuthService = new GithubAuthService();
        ReflectionTestUtils.setField(githubAuthService, "clientId", "client-id-de-prueba");
        ReflectionTestUtils.setField(githubAuthService, "clientSecret", "client-secret-de-prueba");

        RestClient.Builder oauthBuilder = RestClient.builder().baseUrl("https://github.com");
        oauthServer = MockRestServiceServer.bindTo(oauthBuilder).build();
        ReflectionTestUtils.setField(githubAuthService, "oauthClient", oauthBuilder.build());

        RestClient.Builder apiBuilder = RestClient.builder().baseUrl("https://api.github.com");
        apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        ReflectionTestUtils.setField(githubAuthService, "apiClient", apiBuilder.build());
    }

    // ------------------------------------------------------------ exchangeCodeForAccessToken

    @Test
    void exchangeCodeForAccessToken_deberiaRetornarElTokenYEnviarLasCredencialesCorrectas() {
        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andExpect(method(POST))
                .andExpect(header("Accept", "application/json"))
                .andExpect(jsonPath("$.client_id").value("client-id-de-prueba"))
                .andExpect(jsonPath("$.client_secret").value("client-secret-de-prueba"))
                .andExpect(jsonPath("$.code").value("auth-code"))
                .andRespond(withSuccess("{\"access_token\":\"gh-token\",\"token_type\":\"bearer\"}",
                        MediaType.APPLICATION_JSON));

        String token = githubAuthService.exchangeCodeForAccessToken("auth-code");

        assertThat(token).isEqualTo("gh-token");
        oauthServer.verify();
    }

    @Test
    void exchangeCodeForAccessToken_deberiaIncluirRedirectUri_cuandoEstaConfigurado() {
        ReflectionTestUtils.setField(githubAuthService, "redirectUri", "https://app.taskit.com/callback");
        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andExpect(jsonPath("$.redirect_uri").value("https://app.taskit.com/callback"))
                .andRespond(withSuccess("{\"access_token\":\"gh-token\"}", MediaType.APPLICATION_JSON));

        githubAuthService.exchangeCodeForAccessToken("auth-code");

        oauthServer.verify();
    }

    @Test
    void exchangeCodeForAccessToken_deberiaLanzarExcepcion_cuandoGithubRechazaElCodigo() {
        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess(
                        "{\"error\":\"bad_verification_code\",\"error_description\":\"El code ya expiró\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> githubAuthService.exchangeCodeForAccessToken("code-vencido"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("inválido o expirado");
    }

    @Test
    void exchangeCodeForAccessToken_deberiaOmitirRedirectUri_cuandoEstaEnBlancoPeroNoEsNulo() {
        // redirectUri distinto de null pero en blanco: cubre la rama isBlank() del OR (la rama == null
        // ya la cubre el test de arriba, y la rama "con valor" la cubre el de configurado).
        ReflectionTestUtils.setField(githubAuthService, "redirectUri", "   ");
        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess("{\"access_token\":\"gh-token\"}", MediaType.APPLICATION_JSON));

        String token = githubAuthService.exchangeCodeForAccessToken("auth-code");

        assertThat(token).isEqualTo("gh-token");
    }

    @Test
    void exchangeCodeForAccessToken_deberiaLanzarExcepcion_cuandoGithubRespondeSinCuerpo() {
        // Respuesta 204: el body llega null. Cubre la rama "response == null" del OR y del ternario del
        // mensaje de error (distinta de la rama ya cubierta por access_token nulo con cuerpo presente).
        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token")).andRespond(withNoContent());

        assertThatThrownBy(() -> githubAuthService.exchangeCodeForAccessToken("auth-code"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("inválido o expirado");
    }

    @Test
    void exchangeCodeForAccessToken_deberiaLanzarExcepcion_cuandoElAccessTokenVieneVacio() {
        // access_token presente pero en blanco: cubre la tercera rama del OR (distinta de access_token nulo).
        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess("{\"access_token\":\"\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> githubAuthService.exchangeCodeForAccessToken("auth-code"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("inválido o expirado");
    }

    @Test
    void exchangeCodeForAccessToken_deberiaLanzarExternalServiceException_cuandoGithubFalla() {
        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token")).andRespond(withServerError());

        assertThatThrownBy(() -> githubAuthService.exchangeCodeForAccessToken("auth-code"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("GitHub");
    }

    // ------------------------------------------------------------ fetchProfile

    @Test
    void fetchProfile_deberiaUsarElCorreoDeUserCuandoNoEsPrivado() {
        apiServer.expect(requestTo("https://api.github.com/user"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer gh-token"))
                .andRespond(withSuccess(
                        "{\"id\":1,\"login\":\"robin\",\"name\":\"Robinson\",\"email\":\"robin@gmail.com\"}",
                        MediaType.APPLICATION_JSON));

        GithubProfile profile = githubAuthService.fetchProfile("gh-token");

        assertThat(profile.email()).isEqualTo("robin@gmail.com");
        assertThat(profile.name()).isEqualTo("Robinson");
        assertThat(profile.login()).isEqualTo("robin");
        apiServer.verify();
    }

    @Test
    void fetchProfile_deberiaConsultarLosCorreosYTomarElPrimarioVerificado_cuandoElCorreoEsPrivado() {
        apiServer.expect(requestTo("https://api.github.com/user"))
                .andRespond(withSuccess("{\"id\":1,\"login\":\"robin\",\"name\":\"Robinson\",\"email\":null}",
                        MediaType.APPLICATION_JSON));
        apiServer.expect(requestTo("https://api.github.com/user/emails"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "[{\"email\":\"secundario@gmail.com\",\"primary\":false,\"verified\":true},"
                                + "{\"email\":\"principal@gmail.com\",\"primary\":true,\"verified\":true}]",
                        MediaType.APPLICATION_JSON));

        GithubProfile profile = githubAuthService.fetchProfile("gh-token");

        assertThat(profile.email()).isEqualTo("principal@gmail.com");
    }

    @Test
    void fetchProfile_deberiaLanzarExcepcion_cuandoGithubRespondeSinCuerpoDePerfil() {
        // /user devuelve 204: el body llega null. Cubre la rama "user == null", que hasta ahora nunca se
        // ejercitaba porque MockRestServiceServer siempre devolvía un cuerpo en los demás tests.
        apiServer.expect(requestTo("https://api.github.com/user")).andRespond(withNoContent());

        assertThatThrownBy(() -> githubAuthService.fetchProfile("gh-token"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("No se pudo obtener el perfil de GitHub");
    }

    @Test
    void fetchProfile_deberiaConsultarLosCorreosYTomarElPrimarioVerificado_cuandoElCorreoEsUnaCadenaVacia() {
        // email = "" (no null): cubre la rama isBlank() del OR de fetchProfile, distinta de la rama
        // email == null que ya cubre el test de correo privado.
        apiServer.expect(requestTo("https://api.github.com/user"))
                .andRespond(withSuccess("{\"id\":1,\"login\":\"robin\",\"email\":\"\"}",
                        MediaType.APPLICATION_JSON));
        apiServer.expect(requestTo("https://api.github.com/user/emails"))
                .andRespond(withSuccess("[{\"email\":\"principal@gmail.com\",\"primary\":true,\"verified\":true}]",
                        MediaType.APPLICATION_JSON));

        GithubProfile profile = githubAuthService.fetchProfile("gh-token");

        assertThat(profile.email()).isEqualTo("principal@gmail.com");
    }

    @Test
    void fetchProfile_deberiaLanzarExcepcion_cuandoNingunCorreoEstaVerificado() {
        apiServer.expect(requestTo("https://api.github.com/user"))
                .andRespond(withSuccess("{\"id\":1,\"login\":\"robin\",\"email\":null}", MediaType.APPLICATION_JSON));
        apiServer.expect(requestTo("https://api.github.com/user/emails"))
                .andRespond(withSuccess("[{\"email\":\"sin-verificar@gmail.com\",\"primary\":true,\"verified\":false}]",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> githubAuthService.fetchProfile("gh-token"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("correo verificado");
    }

    @Test
    void fetchProfile_deberiaLanzarExternalServiceException_cuandoFallaLaLlamadaAUser() {
        apiServer.expect(requestTo("https://api.github.com/user")).andRespond(withServerError());

        assertThatThrownBy(() -> githubAuthService.fetchProfile("gh-token"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("GitHub");
    }

    @Test
    void fetchProfile_deberiaLanzarExcepcion_cuandoGithubRespondeSinCuerpoDeCorreos() {
        // /user/emails devuelve 204: el body llega null y se normaliza a List.of(). Cubre la rama
        // "emails == null" de fetchVerifiedPrimaryEmail, distinta de la de "ningún correo verificado"
        // (esa ya la cubre el test de arriba con una lista no vacía).
        apiServer.expect(requestTo("https://api.github.com/user"))
                .andRespond(withSuccess("{\"id\":1,\"login\":\"robin\",\"email\":null}", MediaType.APPLICATION_JSON));
        apiServer.expect(requestTo("https://api.github.com/user/emails")).andRespond(withNoContent());

        assertThatThrownBy(() -> githubAuthService.fetchProfile("gh-token"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("correo verificado");
    }

    @Test
    void fetchProfile_deberiaLanzarExternalServiceException_cuandoFallaLaLlamadaAEmails() {
        apiServer.expect(requestTo("https://api.github.com/user"))
                .andRespond(withSuccess("{\"id\":1,\"login\":\"robin\",\"email\":null}", MediaType.APPLICATION_JSON));
        apiServer.expect(requestTo("https://api.github.com/user/emails")).andRespond(withServerError());

        assertThatThrownBy(() -> githubAuthService.fetchProfile("gh-token"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("GitHub");
    }
}