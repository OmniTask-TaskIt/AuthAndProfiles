package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web;

import com.omnitask.AuthAndProfiles.application.services.ClientContext;
import com.omnitask.AuthAndProfiles.application.usecases.ForgotPasswordUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.GithubLoginUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.GoogleLoginUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.LoginUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.ListSessionsUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.LogoutUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.RefreshTokenUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.RegisterUserUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.ResendOtpUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.ResetPasswordUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.RevokeSessionUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.VerifyOtpUseCase;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final GoogleLoginUseCase googleLoginUseCase;
    private final GithubLoginUseCase githubLoginUseCase;
    private final ResendOtpUseCase resendOtpUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ListSessionsUseCase listSessionsUseCase;
    private final RevokeSessionUseCase revokeSessionUseCase;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO request) {
        try {
            registerUserUseCase.execute(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message",
                            "Usuario registrado con éxito. Por favor, verifique su correo con el código OTP enviado."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequestDTO request) {
        verifyOtpUseCase.execute(request);
        return ResponseEntity.ok(Map.of("message", "Correo verificado exitosamente. Ya puede iniciar sesión."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request,
            HttpServletRequest httpRequest) {
        AuthResponseDTO response = loginUseCase.execute(request, getClientContext(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        AuthResponseDTO response = refreshTokenUseCase.execute(request);
        return ResponseEntity.ok(response);
    }

    /** RF-AUTH-4 (paso 1). Responde igual exista o no la cuenta, para no revelar qué correos están registrados. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request,
            HttpServletRequest httpRequest) {
        forgotPasswordUseCase.execute(request.getEmail(), getClientIp(httpRequest));
        return ResponseEntity.ok(Map.of("message",
                "Si el correo está registrado, recibirás un código de recuperación que vence en 15 minutos."));
    }

    /** RF-AUTH-4 (paso 2) y RF-AUTH-14: cambia la contraseña con el código recibido y avisa por correo. */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request,
            HttpServletRequest httpRequest) {
        resetPasswordUseCase.execute(request, getClientIp(httpRequest));
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada. Ya puedes iniciar sesión."));
    }

    /** RF-AUTH-8: requiere sesión; revoca el access token de la petición y borra el refresh token. */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(Authentication authentication,
            @RequestHeader("Authorization") String authorization, HttpServletRequest httpRequest) {
        // Solo se llega aquí con un access token válido, así que el header siempre empieza con "Bearer ".
        String accessToken = authorization.substring(7);
        logoutUseCase.execute(authentication.getName(), accessToken, getClientIp(httpRequest));
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente."));
    }

    /** RF-AUTH-10: sesiones activas del usuario autenticado; la actual viene marcada con current=true. */
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponseDTO>> sessions(Authentication authentication,
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(listSessionsUseCase.execute(authentication.getName(), authorization.substring(7)));
    }

    /** RF-AUTH-10: cierra de forma remota una sesión del usuario sin afectar las demás. */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, String>> revokeSession(Authentication authentication,
            @PathVariable String sessionId) {
        revokeSessionUseCase.execute(authentication.getName(), sessionId);
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente."));
    }

    /** IP, User-Agent y X-Device-Id (identificador estable que genera el front) del cliente. */
    private ClientContext getClientContext(HttpServletRequest request) {
        return new ClientContext(getClientIp(request), request.getHeader("User-Agent"),
                request.getHeader("X-Device-Id"));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponseDTO> googleLogin(@RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        String token = request.get("token");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token de Google es obligatorio");
        }
        AuthResponseDTO response = googleLoginUseCase.execute(token, Boolean.parseBoolean(request.get("acceptedTerms")),
                getClientContext(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/github")
    public ResponseEntity<AuthResponseDTO> githubLogin(@RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código de autorización de GitHub es obligatorio");
        }
        AuthResponseDTO response = githubLoginUseCase.execute(code, Boolean.parseBoolean(request.get("acceptedTerms")),
                getClientContext(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El campo email es obligatorio"));
        }
        try {
            resendOtpUseCase.execute(email);
            return ResponseEntity.ok(Map.of("message", "Código OTP reenviado con éxito. Por favor revise su correo."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}