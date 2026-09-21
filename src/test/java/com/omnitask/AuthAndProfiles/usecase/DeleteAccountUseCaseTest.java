package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.DeleteAccountUseCase;

import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteAccountUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private TokenRedisRepository tokenRedisRepository;

    @InjectMocks
    private DeleteAccountUseCase deleteAccountUseCase;

    @Test
    void execute_deberiaEliminarUsuarioPerfilYToken_cuandoElUsuarioExiste() {
        // Arrange
        User user = User.builder().id("user-1").email("test@gmail.com").build();
        Profile profile = Profile.builder().userId("user-1").build();
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));

        // Act
        deleteAccountUseCase.execute("test@gmail.com");

        // Assert
        verify(tokenRedisRepository).deleteRefreshToken("test@gmail.com");
        verify(profileRepository).delete(profile);
        verify(userRepository).delete(user);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElUsuarioNoExiste() {
        // Arrange
        when(userRepository.findByEmail("noexiste@gmail.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> deleteAccountUseCase.execute("noexiste@gmail.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
        verify(userRepository, never()).delete(any());
    }
}
