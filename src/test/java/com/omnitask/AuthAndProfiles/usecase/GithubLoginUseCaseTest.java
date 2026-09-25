package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.GithubAuthService;
import com.omnitask.AuthAndProfiles.application.services.GithubProfile;
import com.omnitask.AuthAndProfiles.application.services.JwtService;
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
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
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
    private JwtService jwtService;
    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private GithubLoginUseCase githubLoginUseCase;

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
        when(jwtService.generateAccessToken("test@gmail.com", "SEEKER")).thenReturn("access-token");
        when(jwtService.generateRefreshToken("test@gmail.com")).thenReturn("refresh-token");

        // Act
        AuthResponseDTO response = githubLoginUseCase.execute("auth-code");

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
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        // Act
        AuthResponseDTO response = githubLoginUseCase.execute("auth-code");

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
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        // Act
        githubLoginUseCase.execute("auth-code");

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
        assertThatThrownBy(() -> githubLoginUseCase.execute("auth-code"))
                .isInstanceOf(AccountRestrictedException.class)
                .hasMessageContaining("suspendida");
        verify(jwtService, never()).generateAccessToken(any(), any());
        verify(tokenRedisRepository, never()).saveRefreshToken(any(), any(), anyLong());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoGithubNoEntregaUnCorreo() {
        // Arrange
        stubExchange(null, "Robin", "robin");

        // Act & Assert
        assertThatThrownBy(() -> githubLoginUseCase.execute("auth-code"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("correo verificado");
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElCorreoDeGithubVieneEnBlancoPeroNoNulo() {
        // email = "" (no null): cubre la rama isBlank() del "||", distinta de la rama email == null
        // que ya cubre el test de arriba.
        stubExchange("", "Robin", "robin");

        assertThatThrownBy(() -> githubLoginUseCase.execute("auth-code"))
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
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        githubLoginUseCase.execute("auth-code");

        verify(userRepository).save(argThat((User u) -> u.getName().equals("solo-login-2")));
    }
}