package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.services.JwtService;
import com.omnitask.AuthAndProfiles.application.usecases.ChangeAccountStatusUseCase;
import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.AccessRevocationRepository;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeAccountStatusUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenRedisRepository tokenRedisRepository;
    @Mock
    private AccessRevocationRepository accessRevocationRepository;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private ChangeAccountStatusUseCase changeAccountStatusUseCase;

    private User activeUser() {
        return User.builder().id("user-1").email("test@gmail.com").role(Role.PROVIDER)
                .emailVerified(true).accountStatus(AccountStatus.ACTIVE).build();
    }

    @Test
    void execute_deberiaSuspenderYRevocarLosTokens() {
        User user = activeUser();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(jwtService.getAccessTokenExpirationMillis()).thenReturn(900000L);

        User result = changeAccountStatusUseCase.execute("user-1", AccountStatus.SUSPENDED, "  Fraude  ",
                "admin@omnitask.com");

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(result.getBlockReason()).isEqualTo("Fraude");
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(tokenRedisRepository).deleteRefreshToken("test@gmail.com");
        verify(accessRevocationRepository).revokeUser("test@gmail.com", 900000L);
        verify(accessRevocationRepository, never()).clearUserRevocation(anyString());
    }

    @Test
    void execute_deberiaBloquearYRevocarLosTokens() {
        User user = activeUser();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(jwtService.getAccessTokenExpirationMillis()).thenReturn(900000L);

        User result = changeAccountStatusUseCase.execute("user-1", AccountStatus.BLOCKED, "Suplantación",
                "admin@omnitask.com");

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.BLOCKED);
        verify(accessRevocationRepository).revokeUser("test@gmail.com", 900000L);
    }

    @Test
    void execute_deberiaReactivarALaCuentaYLimpiarLaRevocacion() {
        User user = activeUser();
        user.setAccountStatus(AccountStatus.SUSPENDED);
        user.setBlockReason("Fraude");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        User result = changeAccountStatusUseCase.execute("user-1", AccountStatus.ACTIVE, null,
                "admin@omnitask.com");

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.getBlockReason()).isNull();
        verify(accessRevocationRepository).clearUserRevocation("test@gmail.com");
        verify(tokenRedisRepository, never()).deleteRefreshToken(anyString());
        verify(accessRevocationRepository, never()).revokeUser(anyString(), anyLong());
    }

    @Test
    void execute_noDeberiaSaltarLaVerificacionDeCorreo_alReactivar() {
        User user = activeUser();
        user.setEmailVerified(false);
        user.setAccountStatus(AccountStatus.SUSPENDED);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        User result = changeAccountStatusUseCase.execute("user-1", AccountStatus.ACTIVE, null,
                "admin@omnitask.com");

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoFaltaElMotivoAlRestringir() {
        User user = activeUser();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> changeAccountStatusUseCase.execute("user-1", AccountStatus.SUSPENDED, null, "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> changeAccountStatusUseCase.execute("user-1", AccountStatus.BLOCKED, "   ", "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        verify(userRepository, never()).save(user);
        verify(accessRevocationRepository, never()).revokeUser(anyString(), anyLong());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoSeIntentaModificarAUnAdministrador() {
        User admin = User.builder().id("admin-1").email("admin@omnitask.com").role(Role.ADMIN)
                .accountStatus(AccountStatus.ACTIVE).build();
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> changeAccountStatusUseCase.execute("admin-1", AccountStatus.SUSPENDED, "x", "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("administrador");
        verify(userRepository, never()).save(admin);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElEstadoSolicitadoNoEstaPermitido() {
        assertThatThrownBy(
                () -> changeAccountStatusUseCase.execute("user-1", AccountStatus.PENDING_VERIFICATION, "x", "a"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).findById(anyString());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioNoExiste() {
        when(userRepository.findById("nada")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changeAccountStatusUseCase.execute("nada", AccountStatus.SUSPENDED, "x", "a"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Usuario no encontrado");
    }
}
