package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.SearchProfileUseCase;

import com.omnitask.AuthAndProfiles.domain.models.Profile;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchProfileUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private SearchProfileUseCase searchProfileUseCase;

    @Test
    void execute_deberiaBuscarPorEmail_cuandoLaConsultaContieneArroba() {
        // Arrange
        User user = User.builder().id("user-1").email("test@gmail.com").build();
        Profile profile = Profile.builder().userId("user-1").fullName("Robin").build();
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId("user-1")).thenReturn(Optional.of(profile));

        // Act
        List<Profile> result = searchProfileUseCase.execute("test@gmail.com");

        // Assert
        assertThat(result).containsExactly(profile);
    }

    @Test
    void execute_deberiaRetornarListaVacia_cuandoElEmailNoExiste() {
        // Arrange
        when(userRepository.findByEmail("noexiste@gmail.com")).thenReturn(Optional.empty());

        // Act
        List<Profile> result = searchProfileUseCase.execute("noexiste@gmail.com");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void execute_deberiaBuscarPorNombre_cuandoLaConsultaNoEsUnEmail() {
        // Arrange
        User user = User.builder().id("user-1").name("Robin").build();
        Profile profile = Profile.builder().userId("user-1").fullName("Robin").build();
        when(userRepository.findByNameContainingIgnoreCase("Robin")).thenReturn(List.of(user));
        when(profileRepository.findByUserIdIn(List.of("user-1"))).thenReturn(List.of(profile));

        // Act
        List<Profile> result = searchProfileUseCase.execute("Robin");

        // Assert
        assertThat(result).containsExactly(profile);
    }
}
