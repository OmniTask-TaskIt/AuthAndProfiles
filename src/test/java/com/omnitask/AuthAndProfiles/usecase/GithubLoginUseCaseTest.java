package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.GithubAuthService;
import com.omnitask.AuthAndProfiles.application.services.GithubProfile;
import com.omnitask.AuthAndProfiles.application.services.ClientContext;
import com.omnitask.AuthAndProfiles.application.services.SessionService;
import com.omnitask.AuthAndProfiles.application.services.SessionTokens;
import com.omnitask.AuthAndProfiles.application.services.TwoFactorService;
import com.omnitask.AuthAndProfiles.application.usecases.GithubLoginUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.AuthProvider;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.events.UserRegisteredEvent;
import com.omnitask.AuthAndProfiles.domain.exceptions.AccountRestrictedException;
import com.omnitask.AuthAndProfiles.domain.exceptions.AuthenticationFailedException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubLoginUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private GithubAuthService githubAuthService;
    @Mock
    private SessionService sessionService;
    @Mock
    private TwoFactorService twoFactorService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private GithubLoginUseCase githubLoginUseCase;

    private static final ClientContext CONTEXT = new ClientContext("127.0.0.1", "Mozilla/5.0 Chrome/120", "device-1");

    private void stubExchange(String email, String name, String login) {
        when(githubAuthService.exchangeCodeForAccessToken("auth-code")).thenReturn("gh-token");
        when(githubAuthService.fetchProfile("gh-token")).thenReturn(new GithubProfile(email, name, login));
    }

    @Test
    void execute_deberiaRetornarTokens_cuandoElUsuarioDeGithubYaExiste() {
        // Arrange
        User user = User.builder().id("user-1").email("test@gmail.com").role(Role.SEEKER).build();
        stubExchange("test@gmail.com", "Robin", "robin");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(sessionService.openSession(any(User.class), eq(CONTEXT)))
                .thenReturn(new SessionTokens("access-token", "refresh-token"));

        // Act
        AuthResponseDTO response = githubLoginUseCase.execute("auth-code", true, CONTEXT);

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void execute_deberiaCrearUsuarioYPerfilNuevos_cuandoEsElPrimerLoginConGithub() {
        // Arrange
        stubExchange("nuevo@gmail.com", "Nuevo Usuario", "nuevo-login");
        when(userRepository.findByEmail("nuevo@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-nuevo");
            return u;
        });
        when(sessionService.openSession(any(User.class), eq(CONTEXT)))
                .thenReturn(new SessionTokens("access-token", "refresh-token"));

        // Act
        AuthResponseDTO response = githubLoginUseCase.execute("auth-code", true, CONTEXT);

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository).save(
                argThat((User u) -> u.getAuthProvider() == AuthProvider.GITHUB && u.isEmailVerified()
                        && u.getName().equals("Nuevo Usuario")));
        verify(profileRepository).save(any());
        verify(eventPublisher).publish(eq(EventType.USER_REGISTERED), any(), any(UserRegisteredEvent.class));
    }

    @Test
    void execute_deberiaUsarElLogin_cuandoGithubNoDevuelveUnNombre() {
        // Arrange
        stubExchange("sinnombre@gmail.com", null, "solo-login");
        when(userRepository.findByEmail("sinnombre@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionService.openSession(any(User.class), eq(CONTEXT)))
                .thenReturn(new SessionTokens("access-token", "refresh-token"));

        // Act
        githubLoginUseCase.execute("auth-code", true, CONTEXT);

        // Assert
        verify(userRepository).save(argThat((User u) -> u.getName().equals("solo-login")));
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoLaCuentaDeGithubEstaSuspendida() {
        // Arrange
        User user = User.builder().id("user-1").email("test@gmail.com").role(Role.SEEKER)
                .accountStatus(AccountStatus.SUSPENDED).build();
        stubExchange("test@gmail.com", "Robin", "robin");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> githubLoginUseCase.execute("auth-code", true, CONTEXT))
                .isInstanceOf(AccountRestrictedException.class)
                .hasMessageContaining("suspendida");
        verify(sessionService, never()).openSession(any(), any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoGithubNoEntregaUnCorreo() {
        // Arrange
        stubExchange(null, "Robin", "robin");

        // Act & Assert
        assertThatThrownBy(() -> githubLoginUseCase.execute("auth-code", true, CONTEXT))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("correo verificado");
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElCorreoDeGithubVieneEnBlancoPeroNoNulo() {
        // email = "" (no null): cubre la rama isBlank() del "||", distinta de la rama email == null
        // que ya cubre el test de arriba.
        stubExchange("", "Robin", "robin");

        assertThatThrownBy(() -> githubLoginUseCase.execute("auth-code", true, CONTEXT))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("correo verificado");
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void execute_deberiaUsarElLogin_cuandoElNombreDeGithubVieneEnBlancoPeroNoNulo() {
        // name = "" (no null): cubre la rama isBlank() del ternario, distinta de name == null que ya
        // cubre "execute_deberiaUsarElLogin_cuandoGithubNoDevuelveUnNombre".
        stubExchange("sinnombre2@gmail.com", "", "solo-login-2");
        when(userRepository.findByEmail("sinnombre2@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionService.openSession(any(User.class), eq(CONTEXT)))
                .thenReturn(new SessionTokens("access-token", "refresh-token"));

        githubLoginUseCase.execute("auth-code", true, CONTEXT);

        verify(userRepository).save(argThat((User u) -> u.getName().equals("solo-login-2")));
    }

    @Test
    void execute_deberiaRegistrarLaAceptacionDeTerminos_cuandoSeCreaUnUsuarioNuevo() {
        stubExchange("nuevo@gmail.com", "Nuevo Usuario", "login-x");
        when(userRepository.findByEmail("nuevo@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-nuevo");
            return u;
        });
        when(sessionService.openSession(any(User.class), eq(CONTEXT)))
                .thenReturn(new SessionTokens("access-token", "refresh-token"));

        githubLoginUseCase.execute("auth-code", true, CONTEXT);

        verify(userRepository).save(argThat((User u) -> u.isTermsAccepted()
                && u.getTermsAcceptedAt() != null && "1.0".equals(u.getTermsVersion())));
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoUnUsuarioNuevoNoAceptaLosTerminos() {
        stubExchange("nuevo@gmail.com", "Nuevo Usuario", "login-x");
        when(userRepository.findByEmail("nuevo@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubLoginUseCase.execute("auth-code", false, CONTEXT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("términos y condiciones");
        verify(userRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
        verify(sessionService, never()).openSession(any(), any());
    }

    @Test
    void execute_noDeberiaExigirTerminos_cuandoElUsuarioDeGithubYaExiste() {
        User user = User.builder().id("user-1").email("test@gmail.com").role(Role.SEEKER).build();
        stubExchange("test@gmail.com", "Robin", "login-x");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(sessionService.openSession(any(User.class), eq(CONTEXT)))
                .thenReturn(new SessionTokens("access-token", "refresh-token"));

        AuthResponseDTO response = githubLoginUseCase.execute("auth-code", false, CONTEXT);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_deberiaPedirElSegundoFactor_cuandoLaCuentaTiene2FA() {
        // Arrange: sin esto, bastaría con entrar por GitHub para saltarse el segundo factor
        User user = User.builder().id("user-1").email("test@gmail.com").role(Role.SEEKER)
                .twoFactorEnabled(true).build();
        stubExchange("test@gmail.com", "Robin", "robin");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(twoFactorService.startLoginChallenge("test@gmail.com")).thenReturn("challenge-1");

        // Act
        AuthResponseDTO response = githubLoginUseCase.execute("auth-code", true, CONTEXT);

        // Assert
        assertThat(response.isTwoFactorRequired()).isTrue();
        assertThat(response.getChallengeId()).isEqualTo("challenge-1");
        assertThat(response.getAccessToken()).isNull();
        verify(sessionService, never()).openSession(any(), any());
    }
}
