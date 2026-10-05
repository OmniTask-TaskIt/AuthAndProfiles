package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.domain.events.UserRegisteredEvent;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.application.services.OtpGenerator;
import com.omnitask.AuthAndProfiles.application.usecases.RegisterUserUseCase;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;
import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.RegisterRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private ResendEmailService resendEmailService;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private OtpGenerator otpGenerator;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private RegisterUserUseCase registerUserUseCase;

    private RegisterRequestDTO request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequestDTO();
        request.setEmail("test@gmail.com");
        request.setPassword("Password1!");
        request.setName("Test User");
        request.setRole(Role.SEEKER);
        request.setAcceptedTerms(true);
    }

    @Test
    void execute_deberiaCrearUsuarioYPerfilInicial_cuandoElEmailEsNuevo() {
        // Arrange
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());
        when(otpGenerator.generate()).thenReturn("123456");
        when(passwordEncoder.encode("Password1!")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-1");
            return u;
        });

        // Act
        User result = registerUserUseCase.execute(request);

        // Assert
        assertThat(result.getId()).isEqualTo("user-1");
        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        verify(profileRepository).save(argThat(p -> p.getUserId().equals("user-1") && p.getReputationScore() == 5.0f
                && p.getIdentityVerificationStatus() == VerificationStatus.UNVERIFIED));
        verify(resendEmailService).sendOtpEmail("test@gmail.com", "123456");
        verify(tokenRedisRepository).saveRefreshToken("otp:test@gmail.com", "123456", 600000);
        verify(eventPublisher).publish(eq(EventType.USER_REGISTERED), eq("user-1"), any(UserRegisteredEvent.class));
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioYaExiste() {
        // Arrange
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(new User()));

        // Act & Assert
        assertThatThrownBy(() -> registerUserUseCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya está registrado");
        verify(profileRepository, never()).save(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoNoSeAceptanLosTerminos() {
        // Arrange
        request.setAcceptedTerms(false);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> registerUserUseCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("términos");
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoSeIntentaRegistrarComoAdmin() {
        // Arrange
        request.setRole(Role.ADMIN);
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> registerUserUseCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEEKER o PROVIDER");
        verify(userRepository, never()).save(any());
        verify(resendEmailService, never()).sendOtpEmail(anyString(), anyString());
    }

    // ───── Atomicidad del registro: todo o nada ─────

    /** Deja el flujo listo hasta el guardado del usuario y devuelve el usuario "guardado". */
    private User stubUntilUserSaved() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());
        when(otpGenerator.generate()).thenReturn("123456");
        when(passwordEncoder.encode("Password1!")).thenReturn("hashedPassword");
        User saved = new User();
        saved.setId("user-1");
        saved.setEmail("test@gmail.com");
        saved.setName("Test User");
        saved.setRole(Role.SEEKER);
        when(userRepository.save(any(User.class))).thenReturn(saved);
        return saved;
    }

    private Profile stubSavedProfile() {
        Profile profile = Profile.builder().userId("user-1").build();
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));
        return profile;
    }

    @Test
    void execute_deberiaGuardarElOtpAntesDeEnviarElCorreoYPublicarElEventoAlFinal() {
        stubUntilUserSaved();

        registerUserUseCase.execute(request);

        InOrder order = inOrder(userRepository, profileRepository, tokenRedisRepository, resendEmailService,
                eventPublisher);
        order.verify(userRepository).save(any(User.class));
        order.verify(profileRepository).save(any(Profile.class));
        order.verify(tokenRedisRepository).saveRefreshToken("otp:test@gmail.com", "123456", 600000);
        order.verify(resendEmailService).sendOtpEmail("test@gmail.com", "123456");
        order.verify(eventPublisher).publish(eq(EventType.USER_REGISTERED), eq("user-1"),
                any(UserRegisteredEvent.class));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void execute_deberiaRevertirElRegistro_cuandoFallaElEnvioDelCorreo() {
        User saved = stubUntilUserSaved();
        Profile profile = stubSavedProfile();
        doThrow(new ExternalServiceException("Error al enviar el correo de verificación"))
                .when(resendEmailService).sendOtpEmail("test@gmail.com", "123456");

        assertThatThrownBy(() -> registerUserUseCase.execute(request))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("enviar el correo");

        verify(tokenRedisRepository).deleteRefreshToken("otp:test@gmail.com");
        verify(profileRepository).delete(profile);
        verify(userRepository).delete(saved);
        verify(eventPublisher, never()).publish(any(), anyString(), any());
    }

    @Test
    void execute_deberiaRevertirElRegistroYNoEnviarCorreo_cuandoFallaRedis() {
        User saved = stubUntilUserSaved();
        Profile profile = stubSavedProfile();
        doThrow(new IllegalStateException("Redis no disponible"))
                .when(tokenRedisRepository).saveRefreshToken("otp:test@gmail.com", "123456", 600000);

        assertThatThrownBy(() -> registerUserUseCase.execute(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis");

        verify(resendEmailService, never()).sendOtpEmail(anyString(), anyString());
        verify(profileRepository).delete(profile);
        verify(userRepository).delete(saved);
        verify(eventPublisher, never()).publish(any(), anyString(), any());
    }

    @Test
    void execute_deberiaRevertirElRegistro_cuandoFallaLaPublicacionDelEvento() {
        User saved = stubUntilUserSaved();
        Profile profile = stubSavedProfile();
        doThrow(new IllegalStateException("outbox caído"))
                .when(eventPublisher).publish(eq(EventType.USER_REGISTERED), anyString(), any());

        assertThatThrownBy(() -> registerUserUseCase.execute(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox");

        verify(tokenRedisRepository).deleteRefreshToken("otp:test@gmail.com");
        verify(profileRepository).delete(profile);
        verify(userRepository).delete(saved);
    }

    @Test
    void execute_deberiaConservarLaExcepcionOriginal_aunqueFalleElRollback() {
        User saved = stubUntilUserSaved();
        stubSavedProfile();
        doThrow(new ExternalServiceException("Error al enviar el correo de verificación"))
                .when(resendEmailService).sendOtpEmail("test@gmail.com", "123456");
        doThrow(new IllegalStateException("redis caído")).when(tokenRedisRepository).deleteRefreshToken(anyString());
        doThrow(new IllegalStateException("mongo caído")).when(profileRepository).delete(any(Profile.class));
        doThrow(new IllegalStateException("mongo caído")).when(userRepository).delete(any(User.class));

        // Se lanza el error del correo, no los del rollback, y aun así se intentan los tres pasos.
        assertThatThrownBy(() -> registerUserUseCase.execute(request))
                .isInstanceOf(ExternalServiceException.class);

        verify(tokenRedisRepository).deleteRefreshToken("otp:test@gmail.com");
        verify(profileRepository).delete(any(Profile.class));
        verify(userRepository).delete(saved);
    }

    @Test
    void execute_noDeberiaTocarRedisNiEnviarCorreoNiBorrar_cuandoFallaElGuardadoDelUsuario() {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());
        when(otpGenerator.generate()).thenReturn("123456");
        when(passwordEncoder.encode("Password1!")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenThrow(new IllegalStateException("clave duplicada"));

        assertThatThrownBy(() -> registerUserUseCase.execute(request)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(tokenRedisRepository, resendEmailService, eventPublisher, profileRepository);
        verify(userRepository, never()).delete(any(User.class));
    }
}
