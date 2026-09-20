package com.omnitask.AuthAndProfiles.controller;

import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.AuthController;

import com.omnitask.AuthAndProfiles.application.usecases.*;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private RegisterUserUseCase registerUserUseCase;
    @Mock
    private VerifyOtpUseCase verifyOtpUseCase;
    @Mock
    private LoginUseCase loginUseCase;
    @Mock
    private RefreshTokenUseCase refreshTokenUseCase;
    @Mock
    private GoogleLoginUseCase googleLoginUseCase;
    @Mock
    private ResendOtpUseCase resendOtpUseCase;
    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AuthController authController;

    @Test
    void register_deberiaRetornar201_cuandoElRegistroEsExitoso() {
        // Arrange
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail("test@gmail.com");

        // Act
        ResponseEntity<?> response = authController.register(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(registerUserUseCase).execute(request);
    }

    @Test
    void register_deberiaRetornar409_cuandoElEmailYaEstaRegistrado() {
        // Arrange
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail("test@gmail.com");
        doThrow(new IllegalArgumentException("El usuario ya está registrado."))
                .when(registerUserUseCase).execute(request);

        // Act
        ResponseEntity<?> response = authController.register(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void login_deberiaRetornarLaRespuestaDeAutenticacion_conLaIpDelHeader() {
        // Arrange
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("test@gmail.com");
        request.setPassword("Password1!");
        AuthResponseDTO expected = new AuthResponseDTO("access", "refresh", "ok", "test@gmail.com");

        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
        when(loginUseCase.execute(request, "203.0.113.5")).thenReturn(expected);

        // Act
        ResponseEntity<AuthResponseDTO> response = authController.login(request, httpServletRequest);

        // Assert
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loginUseCase).execute(request, "203.0.113.5");
    }

    @Test
    void login_deberiaUsarLaIpRemota_cuandoNoHayHeaderXForwardedFor() {
        // Arrange
        LoginRequestDTO request = new LoginRequestDTO();
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.0.10");
        when(loginUseCase.execute(eq(request), anyString()))
                .thenReturn(new AuthResponseDTO("a", "r", "ok", "e"));

        // Act
        authController.login(request, httpServletRequest);

        // Assert
        verify(loginUseCase).execute(request, "192.168.0.10");
    }

    @Test
    void googleLogin_deberiaLanzarExcepcion_cuandoNoSeEnviaElToken() {
        // Arrange
        Map<String, String> body = Map.of();

        // Act & Assert
        assertThatThrownBy(() -> authController.googleLogin(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El token de Google es obligatorio");
    }

    @Test
    void resendOtp_deberiaRetornar400_cuandoNoSeEnviaElEmail() {
        // Arrange
        Map<String, String> body = Map.of();

        // Act
        ResponseEntity<?> response = authController.resendOtp(body);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(resendOtpUseCase, never()).execute(anyString());
    }

    @Test
    void resendOtp_deberiaRetornar200_cuandoElReenvioEsExitoso() {
        // Arrange
        Map<String, String> body = Map.of("email", "test@gmail.com");

        // Act
        ResponseEntity<?> response = authController.resendOtp(body);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(resendOtpUseCase).execute("test@gmail.com");
    }
}
