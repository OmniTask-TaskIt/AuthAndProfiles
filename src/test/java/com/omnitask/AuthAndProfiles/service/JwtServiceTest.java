package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Arrange: JwtService usa campos @Value, así que se inyectan por reflexión (no hay Spring context en un test unitario)
        ReflectionTestUtils.setField(jwtService, "secretKey",
                "clave-secreta-de-prueba-con-suficiente-longitud-para-hmac-sha-256-1234567890");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 900000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604800000L);
    }

    @Test
    void generateAccessToken_deberiaGenerarUnTokenDelQueSePuedaExtraerElEmail() {
        // Act
        String token = jwtService.generateAccessToken("test@gmail.com", "SEEKER");

        // Assert
        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo("test@gmail.com");
    }

    @Test
    void isTokenValid_deberiaRetornarTrue_cuandoElEmailCoincideYNoHaExpirado() {
        // Arrange
        String token = jwtService.generateAccessToken("test@gmail.com", "SEEKER");

        // Act
        boolean valid = jwtService.isTokenValid(token, "test@gmail.com");

        // Assert
        assertThat(valid).isTrue();
    }

    @Test
    void isTokenValid_deberiaRetornarFalse_cuandoElEmailNoCoincide() {
        // Arrange
        String token = jwtService.generateAccessToken("test@gmail.com", "SEEKER");

        // Act
        boolean valid = jwtService.isTokenValid(token, "otro@gmail.com");

        // Assert
        assertThat(valid).isFalse();
    }

    @Test
    void generateRefreshToken_deberiaGenerarUnTokenValidoSinClaimDeRol() {
        // Act
        String token = jwtService.generateRefreshToken("test@gmail.com");

        // Assert
        assertThat(jwtService.extractEmail(token)).isEqualTo("test@gmail.com");
    }
}
