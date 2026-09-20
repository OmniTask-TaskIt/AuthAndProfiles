package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.GoogleAuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NOTA IMPORTANTE:
 * GoogleAuthService construye el GoogleIdTokenVerifier directamente dentro del método
 * (no se inyecta como dependencia), y para un token con formato válido, verify() necesita
 * llamar a los servidores de Google para validar la firma. Eso lo hace imposible de
 * testear como unidad pura para el camino feliz sin mockear una librería estática de
 * terceros (no hay una "costura" para inyectar un mock).
 * <p>
 * Lo único que sí se puede probar sin red es el camino de error con un token mal formado,
 * porque GoogleIdTokenVerifier falla al parsear el JWT ANTES de intentar contactar a Google.
 * <p>
 * Recomendación: si se quiere cobertura real del camino feliz, envolver
 * GoogleIdTokenVerifier detrás de una interfaz propia (ej. GoogleTokenVerifierPort) inyectada
 * por constructor, para poder mockearla en el test.
 */
class GoogleAuthServiceTest {

    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthService();
        ReflectionTestUtils.setField(googleAuthService, "googleClientId", "fake-client-id.apps.googleusercontent.com");
    }

    @Test
    void verifyToken_deberiaLanzarExcepcion_cuandoElTokenTieneFormatoInvalido() {
        // Arrange
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
