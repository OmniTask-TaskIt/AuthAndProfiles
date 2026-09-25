package com.omnitask.AuthAndProfiles.controller;

import com.omnitask.AuthAndProfiles.domain.exceptions.AccountRestrictedException;
import com.omnitask.AuthAndProfiles.domain.exceptions.AuthenticationFailedException;
import com.omnitask.AuthAndProfiles.domain.exceptions.ConflictException;
import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.exceptions.TooManyAttemptsException;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private void assertResponse(ResponseEntity<Map<String, Object>> response, HttpStatus status, String message) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().get("status")).isEqualTo(status.value());
        assertThat(response.getBody().get("message")).isEqualTo(message);
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void deberiaMapearCadaExcepcionDeDominioAsuEstadoHttp() {
        assertResponse(handler.handleIllegalArgument(new IllegalArgumentException("mal")), HttpStatus.BAD_REQUEST,
                "mal");
        assertResponse(handler.handleNotFound(new NotFoundException("no está")), HttpStatus.NOT_FOUND, "no está");
        assertResponse(handler.handleAuthentication(new AuthenticationFailedException("credenciales")),
                HttpStatus.UNAUTHORIZED, "credenciales");
        assertResponse(handler.handleAuthentication(new BadCredentialsException("spring")),
                HttpStatus.UNAUTHORIZED, "spring");
        assertResponse(handler.handleAccountRestricted(new AccountRestrictedException("suspendida")),
                HttpStatus.FORBIDDEN, "suspendida");
        assertResponse(handler.handleConflict(new ConflictException("ya calificaste")),
                HttpStatus.CONFLICT, "ya calificaste");
        assertResponse(handler.handleTooManyAttempts(new TooManyAttemptsException("muchos")),
                HttpStatus.TOO_MANY_REQUESTS, "muchos");
        assertResponse(handler.handleExternalService(new ExternalServiceException("resend caído")),
                HttpStatus.BAD_GATEWAY, "resend caído");
    }

    @Test
    void handleAccessDenied_noDeberiaExponerElDetalleInterno() {
        assertResponse(handler.handleAccessDenied(new AccessDeniedException("detalle interno")),
                HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta acción.");
    }

    @Test
    void handleUnexpected_deberiaResponder500ConMensajeGenerico() {
        assertResponse(handler.handleUnexpected(new RuntimeException("NPE en la línea 42")),
                HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intenta nuevamente más tarde.");
    }
}
