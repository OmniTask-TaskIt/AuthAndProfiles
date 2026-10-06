package com.omnitask.AuthAndProfiles.controller;

import com.omnitask.AuthAndProfiles.application.services.ClientContext;
import com.omnitask.AuthAndProfiles.application.usecases.GithubLoginUseCase;
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
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
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
    private GithubLoginUseCase githubLoginUseCase;
    @Mock
    private ResendOtpUseCase resendOtpUseCase;
    @Mock
    private ForgotPasswordUseCase forgotPasswordUseCase;
    @Mock
    private ResetPasswordUseCase resetPasswordUseCase;
    @Mock
    private LogoutUseCase logoutUseCase;
    @Mock
    private ListSessionsUseCase listSessionsUseCase;
    @Mock
    private RevokeSessionUseCase revokeSessionUseCase;
    @Mock
    private Authentication authentication;
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

        ClientContext context = new ClientContext("203.0.113.5", "Mozilla/5.0 Chrome/120", "device-1");
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0 Chrome/120");
        when(httpServletRequest.getHeader("X-Device-Id")).thenReturn("device-1");
        when(loginUseCase.execute(request, context)).thenReturn(expected);

        // Act
        ResponseEntity<AuthResponseDTO> response = authController.login(request, httpServletRequest);

        // Assert
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loginUseCase).execute(request, context);
    }

    @Test
    void login_deberiaUsarLaIpRemota_cuandoNoHayHeaderXForwardedFor() {
        // Arrange
        LoginRequestDTO request = new LoginRequestDTO();
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.0.10");
        when(loginUseCase.execute(eq(request), any(ClientContext.class)))
                .thenReturn(new AuthResponseDTO("a", "r", "ok", "e"));

        // Act
        authController.login(request, httpServletRequest);

        // Assert
        verify(loginUseCase).execute(request, new ClientContext("192.168.0.10", null, null));
    }

    @Test
    void googleLogin_deberiaLanzarExcepcion_cuandoNoSeEnviaElToken() {
        // Arrange
        Map<String, String> body = Map.of();

        // Act & Assert
        assertThatThrownBy(() -> authController.googleLogin(body, httpServletRequest))
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

    @Test
    void verifyOtp_deberiaRetornar200YDelegarEnElUseCase() {
        // Arrange
        VerifyOtpRequestDTO request = new VerifyOtpRequestDTO();
        request.setEmail("test@gmail.com");
        request.setOtpCode("123456");

        // Act
        ResponseEntity<?> response = authController.verifyOtp(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) response.getBody()).get("message")).asString().contains("Correo verificado");
        verify(verifyOtpUseCase).execute(request);
    }

    @Test
    void refresh_deberiaRetornarLaRespuestaDelUseCase() {
        // Arrange
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO();
        request.setEmail("test@gmail.com");
        request.setRefreshToken("refresh");
        AuthResponseDTO expected = new AuthResponseDTO("nuevo-access", "refresh", "Token renovado con éxito",
                "test@gmail.com");
        when(refreshTokenUseCase.execute(request)).thenReturn(expected);

        // Act
        ResponseEntity<AuthResponseDTO> response = authController.refresh(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void googleLogin_deberiaRetornarLaRespuestaDelUseCase_cuandoElTokenEsValido() {
        // Arrange
        AuthResponseDTO expected = new AuthResponseDTO("access", "refresh", "ok", "test@gmail.com");
        when(googleLoginUseCase.execute("google-token", true, new ClientContext(null, null, null))).thenReturn(expected);

        // Act
        ResponseEntity<AuthResponseDTO> response = authController.googleLogin(Map.of("token", "google-token", "acceptedTerms", "true"), httpServletRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void googleLogin_deberiaLanzarExcepcion_cuandoElTokenEstaEnBlanco() {
        // Arrange
        Map<String, String> body = Map.of("token", "   ");

        // Act & Assert
        assertThatThrownBy(() -> authController.googleLogin(body, httpServletRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El token de Google es obligatorio");
        verify(googleLoginUseCase, never()).execute(anyString(), anyBoolean(), any());
    }

    @Test
    void resendOtp_deberiaRetornar400_cuandoElEmailEstaEnBlanco() {
        // Arrange
        Map<String, String> body = Map.of("email", "  ");

        // Act
        ResponseEntity<?> response = authController.resendOtp(body);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("El campo email es obligatorio");
        verify(resendOtpUseCase, never()).execute(anyString());
    }

    @Test
    void resendOtp_deberiaRetornar400ConElMensaje_cuandoElUseCaseFalla() {
        // Arrange
        Map<String, String> body = Map.of("email", "test@gmail.com");
        doThrow(new RuntimeException("Usuario no encontrado")).when(resendOtpUseCase).execute("test@gmail.com");

        // Act
        ResponseEntity<?> response = authController.resendOtp(body);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Usuario no encontrado");
    }

    @Test
    void githubLogin_deberiaRetornarLaRespuestaDelUseCase_cuandoElCodigoEsValido() {
        // Arrange
        AuthResponseDTO expected = new AuthResponseDTO("access", "refresh", "ok", "test@gmail.com");
        when(githubLoginUseCase.execute("auth-code", true, new ClientContext(null, null, null))).thenReturn(expected);

        // Act
        ResponseEntity<AuthResponseDTO> response = authController.githubLogin(Map.of("code", "auth-code", "acceptedTerms", "true"), httpServletRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void githubLogin_deberiaLanzarExcepcion_cuandoNoSeEnviaElCodigo() {
        // Arrange
        Map<String, String> body = Map.of();

        // Act & Assert
        assertThatThrownBy(() -> authController.githubLogin(body, httpServletRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("código de autorización de GitHub");
        verify(githubLoginUseCase, never()).execute(anyString(), anyBoolean(), any());
    }

    @Test
    void githubLogin_deberiaLanzarExcepcion_cuandoElCodigoEstaEnBlanco() {
        // Arrange
        Map<String, String> body = Map.of("code", "   ");

        // Act & Assert
        assertThatThrownBy(() -> authController.githubLogin(body, httpServletRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("código de autorización de GitHub");
        verify(githubLoginUseCase, never()).execute(anyString(), anyBoolean(), any());
    }

    @Test
    void googleLogin_deberiaEnviarAcceptedTermsFalse_cuandoElCampoNoViene() {
        AuthResponseDTO expected = new AuthResponseDTO("access", "refresh", "ok", "test@gmail.com");
        when(googleLoginUseCase.execute("google-token", false, new ClientContext(null, null, null))).thenReturn(expected);

        authController.googleLogin(Map.of("token", "google-token"), httpServletRequest);

        verify(googleLoginUseCase).execute("google-token", false, new ClientContext(null, null, null));
    }

    @Test
    void githubLogin_deberiaEnviarAcceptedTermsFalse_cuandoElCampoNoViene() {
        AuthResponseDTO expected = new AuthResponseDTO("access", "refresh", "ok", "test@gmail.com");
        when(githubLoginUseCase.execute("auth-code", false, new ClientContext(null, null, null))).thenReturn(expected);

        authController.githubLogin(Map.of("code", "auth-code"), httpServletRequest);

        verify(githubLoginUseCase).execute("auth-code", false, new ClientContext(null, null, null));
    }

    @Test
    void forgotPassword_deberiaResponder200ConUnMensajeGenerico_yPasarLaIpDelCliente() {
        // Arrange
        ForgotPasswordRequestDTO request = new ForgotPasswordRequestDTO();
        request.setEmail("test@gmail.com");
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");

        // Act
        ResponseEntity<Map<String, String>> response = authController.forgotPassword(request, httpServletRequest);

        // Assert: el mensaje no revela si el correo existe
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        assertThat(response.getBody().get("message")).contains("Si el correo está registrado");
        verify(forgotPasswordUseCase).execute("test@gmail.com", "203.0.113.5");
    }

    @Test
    void resetPassword_deberiaResponder200_yDelegarEnElCasoDeUso() {
        // Arrange
        ResetPasswordRequestDTO request = new ResetPasswordRequestDTO();
        request.setEmail("test@gmail.com");
        request.setCode("123456");
        request.setNewPassword("NuevaClave1!");
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.0.10");

        // Act
        ResponseEntity<Map<String, String>> response = authController.resetPassword(request, httpServletRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("message")).contains("Contraseña actualizada");
        verify(resetPasswordUseCase).execute(request, "192.168.0.10");
    }

    @Test
    void logout_deberiaRevocarElAccessTokenDelHeader_conElCorreoDeLaSesion() {
        // Arrange
        when(authentication.getName()).thenReturn("test@gmail.com");
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5");

        // Act
        ResponseEntity<Map<String, String>> response = authController.logout(authentication, "Bearer access-token",
                httpServletRequest);

        // Assert: el token llega sin el prefijo "Bearer "
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("message")).isEqualTo("Sesión cerrada correctamente.");
        verify(logoutUseCase).execute("test@gmail.com", "access-token", "203.0.113.5");
    }

    @Test
    void sessions_deberiaListarLasSesionesDelUsuario_conElTokenSinElPrefijoBearer() {
        // Arrange
        SessionResponseDTO sesion = SessionResponseDTO.builder().id("sesion-1").deviceInfo("Chrome en Windows")
                .ipAddress("203.0.113.5").createdAt(Instant.now()).lastActiveAt(Instant.now())
                .current(true).build();
        when(authentication.getName()).thenReturn("test@gmail.com");
        when(listSessionsUseCase.execute("test@gmail.com", "access-token")).thenReturn(List.of(sesion));

        // Act
        ResponseEntity<List<SessionResponseDTO>> response = authController.sessions(authentication,
                "Bearer access-token");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(sesion);
    }

    @Test
    void revokeSession_deberiaCerrarLaSesionIndicada_yResponder200() {
        // Arrange
        when(authentication.getName()).thenReturn("test@gmail.com");

        // Act
        ResponseEntity<Map<String, String>> response = authController.revokeSession(authentication, "sesion-2");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("message")).isEqualTo("Sesión cerrada correctamente.");
        verify(revokeSessionUseCase).execute("test@gmail.com", "sesion-2");
    }
}
