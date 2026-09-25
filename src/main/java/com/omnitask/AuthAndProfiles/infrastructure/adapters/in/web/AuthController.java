package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web;

import com.omnitask.AuthAndProfiles.application.usecases.GithubLoginUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.GoogleLoginUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.LoginUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.RefreshTokenUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.RegisterUserUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.ResendOtpUseCase;
import com.omnitask.AuthAndProfiles.application.usecases.VerifyOtpUseCase;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        String clientIp = getClientIp(httpRequest);
        AuthResponseDTO response = loginUseCase.execute(request, clientIp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        AuthResponseDTO response = refreshTokenUseCase.execute(request);
        return ResponseEntity.ok(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponseDTO> googleLogin(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token de Google es obligatorio");
        }
        AuthResponseDTO response = googleLoginUseCase.execute(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/github")
    public ResponseEntity<AuthResponseDTO> githubLogin(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código de autorización de GitHub es obligatorio");
        }
        AuthResponseDTO response = githubLoginUseCase.execute(code);
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
