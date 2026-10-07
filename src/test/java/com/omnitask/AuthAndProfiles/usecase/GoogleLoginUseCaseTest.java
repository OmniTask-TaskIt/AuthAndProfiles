package com.omnitask.AuthAndProfiles.usecase;

import static org.mockito.ArgumentMatchers.eq;
import com.omnitask.AuthAndProfiles.domain.events.UserRegisteredEvent;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.application.usecases.GoogleLoginUseCase;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.omnitask.AuthAndProfiles.application.services.GoogleAuthService;
import com.omnitask.AuthAndProfiles.application.services.ClientContext;
import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.application.services.SessionTokens;
import com.omnitask.AuthAndProfiles.application.services.TwoFactorService;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.exceptions.AccountRestrictedException;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.AuthResponseDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleLoginUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private GoogleAuthService googleAuthService;
    @Mock
    private SessionService sessionService;
    @Mock
    private TwoFactorService twoFactorService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private GoogleLoginUseCase googleLoginUseCase;

    private static final ClientContext CONTEXT = new ClientContext("127.0.0.1", "Mozilla/5.0 Chrome/120", "device-1");

    private GoogleIdToken.Payload payloadFor(String email, String name) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.set("name", name);
        return payload;
    }

    @Test
    void execute_deberiaRetornarTokens_cuandoElUsuarioDeGoogleYaExiste() {
        // Arrange
        User user = User.builder().id("user-1").email("test@gmail.com").role(Role.SEEKER).build();
        when(googleAuthService.verifyToken("google-token")).thenReturn(payloadFor("test@gmail.com", "Robin"));
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(sessionService.openSession(any(User.class), eq(CONTEXT)))
                .thenReturn(new SessionTokens("access-token", "refresh-token"));

        // Act
        AuthResponseDTO response = googleLoginUseCase.execute("google-token", true, CONTEXT);

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void execute_deberiaCrearUsuarioYPerfilNuevos_cuandoEsElPrimerLoginConGoogle() {
        // Arrange
        when(googleAuthService.verifyToken("google-token")).thenReturn(payloadFor("nuevo@gmail.com", "Nuevo Usuario"));
        when(userRepository.findByEmail("nuevo@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-nuevo");
            return u;
        });
        when(sessionService.openSession(any(User.class), eq(CONTEXT)))
                .thenReturn(new SessionTokens("access-token", "refresh-token"));

        // Act
        AuthResponseDTO response = googleLoginUseCase.execute("google-token", true, CONTEXT);

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository).save(argThat((User u) -> u.getAuthProvider() == AuthProvider.GOOGLE && u.isEmailVerified()));
        verify(profileRepository).save(any());
        verify(eventPublisher).publish(eq(EventType.USER_REGISTERED), any(), any(UserRegisteredEvent.class));
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoLaCuentaDeGoogleEstaSuspendida() {
        // Arrange
        User user = User.builder().id("user-1").email("test@gmail.com").role(Role.SEEKER)
                .accountStatus(AccountStatus.SUSPENDED).build();
        when(googleAuthService.verifyToken("google-token")).thenReturn(payloadFor("test@gmail.com", "Robin"));
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> googleLoginUseCase.execute("google-token", true, CONTEXT))
                .isInstanceOf(AccountRestrictedException.class)
                .hasMessageContaining("suspendida");
        verify(sessionService, never()).openSession(any(), any());
    }

    @Test
    void execute_deberiaRegistrarLaAceptacionDeTerminos_cuandoSeCreaUnUsuarioNuevo() {
        when(googleAuthService.verifyToken("google-token")).thenReturn(payloadFor("nuevo@gmail.com", "Nuevo Usuario"));
        when(userRepository.findByEmail("nuevo@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-nuevo");
            return u;
        });
        when(sessionService.openSession(any(User.class), eq(CONTEXT)))
                .thenReturn(new SessionTokens("access-token", "refresh-token"));

        googleLoginUseCase.execute("google-token", true, CONTEXT);

        verify(userRepository).save(argThat((User u) -> u.isTermsAccepted()
                && u.getTermsAcceptedAt() != null && "1.0".equals(u.getTermsVersion())));
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoUnUsuarioNuevoNoAceptaLosTerminos() {
        when(googleAuthService.verifyToken("google-token")).thenReturn(payloadFor("nuevo@gmail.com", "Nuevo Usuario"));
        when(userRepository.findByEmail("nuevo@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> googleLoginUseCase.execute("google-token", false, CONTEXT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("términos y condiciones");
        verify(userRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
        verify(sessionService, never()).openSession(any(), any());
    }

    @Test
    void execute_noDeberiaExigirTerminos_cuandoElUsuarioDeGoogleYaExiste() {
        User user = User.builder().id("user-1").email("test@gmail.com").role(Role.SEEKER).build();
        when(googleAuthService.verifyToken("google-token")).thenReturn(payloadFor("test@gmail.com", "Robin"));
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(sessionService.openSession(any(User.class), eq(CONTEXT)))
                .thenReturn(new SessionTokens("access-token", "refresh-token"));

        AuthResponseDTO response = googleLoginUseCase.execute("google-token", false, CONTEXT);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_deberiaPedirElSegundoFactor_cuandoLaCuentaTiene2FA() {
        // Arrange: sin esto, bastaría con entrar por Google para saltarse el segundo factor
        User user = User.builder().id("user-1").email("test@gmail.com").role(Role.SEEKER)
                .twoFactorEnabled(true).build();
        when(googleAuthService.verifyToken("google-token")).thenReturn(payloadFor("test@gmail.com", "Robin"));
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(twoFactorService.startLoginChallenge("test@gmail.com")).thenReturn("challenge-1");

        // Act
        AuthResponseDTO response = googleLoginUseCase.execute("google-token", true, CONTEXT);

        // Assert
        assertThat(response.isTwoFactorRequired()).isTrue();
        assertThat(response.getChallengeId()).isEqualTo("challenge-1");
        assertThat(response.getAccessToken()).isNull();
        verify(sessionService, never()).openSession(any(), any());
    }
}
