package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.GoogleAuthService;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * GoogleAuthService crea el GoogleIdTokenVerifier con "new" dentro del método, así que para cubrir
 * el camino feliz (y el de token nulo) se intercepta la construcción del Builder con
 * Mockito.mockConstruction (mock-maker inline, incluido por defecto en Mockito 5 / Spring Boot 3.3).
 * Así no se hace ninguna llamada de red a Google.
 */
class GoogleAuthServiceTest {

    private static final String TOKEN = "token-de-prueba";

    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthService();
        ReflectionTestUtils.setField(googleAuthService, "googleClientId", "fake-client-id.apps.googleusercontent.com");
    }

    @Test
    void verifyToken_deberiaRetornarElPayload_cuandoElTokenEsValido() throws Exception {
        // Arrange
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("test@gmail.com");
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify(TOKEN)).thenReturn(idToken);

        try (MockedConstruction<GoogleIdTokenVerifier.Builder> ignored = mockConstruction(
                GoogleIdTokenVerifier.Builder.class, (builder, context) -> {
                    when(builder.setAudience(any())).thenReturn(builder);
                    when(builder.build()).thenReturn(verifier);
                })) {

            // Act
            GoogleIdToken.Payload result = googleAuthService.verifyToken(TOKEN);

            // Assert
            assertThat(result).isSameAs(payload);
            assertThat(result.getEmail()).isEqualTo("test@gmail.com");
        }
    }

    @Test
    void verifyToken_deberiaLanzarExcepcion_cuandoGoogleNoReconoceElToken() throws Exception {
        // Arrange: verify() devuelve null cuando el token es inválido o expiró
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify(TOKEN)).thenReturn(null);

        try (MockedConstruction<GoogleIdTokenVerifier.Builder> ignored = mockConstruction(
                GoogleIdTokenVerifier.Builder.class, (builder, context) -> {
                    when(builder.setAudience(any())).thenReturn(builder);
                    when(builder.build()).thenReturn(verifier);
                })) {

            // Act & Assert
            assertThatThrownBy(() -> googleAuthService.verifyToken(TOKEN))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error de seguridad verificando el token de Google")
                    .hasMessageContaining("Token de Google inválido o expirado");
        }
    }

    @Test
    void verifyToken_deberiaEnvolverLaExcepcion_cuandoElVerificadorFalla() throws Exception {
        // Arrange
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify(TOKEN)).thenThrow(new GeneralSecurityException("firma inválida"));

        try (MockedConstruction<GoogleIdTokenVerifier.Builder> ignored = mockConstruction(
                GoogleIdTokenVerifier.Builder.class, (builder, context) -> {
                    when(builder.setAudience(any())).thenReturn(builder);
                    when(builder.build()).thenReturn(verifier);
                })) {

            // Act & Assert
            assertThatThrownBy(() -> googleAuthService.verifyToken(TOKEN))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error de seguridad verificando el token de Google")
                    .hasMessageContaining("firma inválida");
        }
    }

    @Test
    void verifyToken_deberiaLanzarExcepcion_cuandoElTokenTieneFormatoInvalido() {
        // Arrange: sin mocks, el verificador real falla al parsear el JWT antes de contactar a Google
        String tokenMalformado = "esto-no-es-un-jwt-valido";

        // Act & Assert
        assertThatThrownBy(() -> googleAuthService.verifyToken(tokenMalformado))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error de seguridad verificando el token de Google");
    }

    @Test
    void verifyToken_deberiaLanzarExcepcion_cuandoElTokenEsNulo() {
        // Act & Assert
        assertThatThrownBy(() -> googleAuthService.verifyToken(null))
                .isInstanceOf(RuntimeException.class);
    }
}