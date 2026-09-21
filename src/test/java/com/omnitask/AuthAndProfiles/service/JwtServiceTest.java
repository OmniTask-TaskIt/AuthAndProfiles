package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // JwtService usa campos @Value; en un test unitario (sin Spring) se inyectan por reflexión
        ReflectionTestUtils.setField(jwtService, "secretKey",
                "clave-secreta-de-prueba-con-suficiente-longitud-para-hmac-sha-256-1234567890");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 900000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604800000L);
    }

    @Test
    void generateAccessToken_deberiaGenerarUnTokenDelQueSePuedaExtraerElEmail() {
        String token = jwtService.generateAccessToken("test@gmail.com", "SEEKER");

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo("test@gmail.com");
    }

    @Test
    void generateAccessToken_deberiaIncluirElClaimDeRol_cuandoElRolTieneValor() {
        String token = jwtService.generateAccessToken("test@gmail.com", "SEEKER");

        assertThat(jwtService.extractRole(token)).isEqualTo("SEEKER");
    }

    @Test
    void generateAccessToken_noDeberiaIncluirElClaimDeRol_cuandoElRolEsNulo() {
        String token = jwtService.generateAccessToken("test@gmail.com", null);

        assertThat(jwtService.extractRole(token)).isNull();
        assertThat(jwtService.extractEmail(token)).isEqualTo("test@gmail.com");
    }

    @Test
    void isTokenValid_deberiaRetornarTrue_cuandoElEmailCoincideYNoHaExpirado() {
        String token = jwtService.generateAccessToken("test@gmail.com", "SEEKER");

        assertThat(jwtService.isTokenValid(token, "test@gmail.com")).isTrue();
    }

    @Test
    void isTokenValid_deberiaRetornarFalse_cuandoElEmailNoCoincide() {
        String token = jwtService.generateAccessToken("test@gmail.com", "SEEKER");

        assertThat(jwtService.isTokenValid(token, "otro@gmail.com")).isFalse();
    }

    @Test
    void isTokenValid_deberiaRetornarFalse_cuandoElEmailCoincidePeroLaFechaDeExpiracionYaPaso() {
        // El parser real de JJWT lanza ExpiredJwtException antes de que isTokenExpired pueda devolver true,
        // por eso se simulan los claims con un spy sobre extractClaim.
        JwtService jwtServiceSpy = spy(jwtService);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("test@gmail.com");
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() - 60_000));

        doAnswer(invocation -> {
            Function<Claims, ?> resolver = invocation.getArgument(1);
            return resolver.apply(claims);
        }).when(jwtServiceSpy).extractClaim(anyString(), any());

        assertThat(jwtServiceSpy.isTokenValid("cualquier-token", "test@gmail.com")).isFalse();
    }

    @Test
    void isTokenValid_deberiaLanzarExpiredJwtException_cuandoElTokenRealYaExpiro() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L);
        String tokenExpirado = jwtService.generateAccessToken("test@gmail.com", "SEEKER");

        assertThatThrownBy(() -> jwtService.isTokenValid(tokenExpirado, "test@gmail.com"))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void generateRefreshToken_deberiaGenerarUnTokenValidoSinClaimDeRol() {
        String token = jwtService.generateRefreshToken("test@gmail.com");

        assertThat(jwtService.extractEmail(token)).isEqualTo("test@gmail.com");
        assertThat(jwtService.extractRole(token)).isNull();
    }

    @Test
    void getAccessTokenExpirationMillis_deberiaRetornarLaExpiracionConfigurada() {
        assertThat(jwtService.getAccessTokenExpirationMillis()).isEqualTo(900000L);
    }
}
