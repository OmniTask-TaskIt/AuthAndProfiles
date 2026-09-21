package com.omnitask.AuthAndProfiles.security;

import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import com.omnitask.AuthAndProfiles.infrastructure.security.ProfileSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileSecurityTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProfileSecurity profileSecurity;

    private Authentication authenticationOf(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of());
    }

    @Test
    void isOwner_deberiaRetornarTrue_cuandoElUserIdPerteneceAlUsuarioAutenticado() {
        User user = User.builder().id("user-1").email("test@gmail.com").build();
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        assertThat(profileSecurity.isOwner("user-1", authenticationOf("test@gmail.com"))).isTrue();
    }

    @Test
    void isOwner_deberiaRetornarFalse_cuandoElUserIdEsDeOtroUsuario() {
        User user = User.builder().id("user-1").email("test@gmail.com").build();
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        assertThat(profileSecurity.isOwner("user-2", authenticationOf("test@gmail.com"))).isFalse();
    }

    @Test
    void isOwner_deberiaRetornarFalse_cuandoElUsuarioAutenticadoNoExisteEnBd() {
        when(userRepository.findByEmail("fantasma@gmail.com")).thenReturn(Optional.empty());

        assertThat(profileSecurity.isOwner("user-1", authenticationOf("fantasma@gmail.com"))).isFalse();
    }

    @Test
    void isOwner_deberiaRetornarFalseSinConsultarLaBd_cuandoFaltanDatos() {
        Authentication noAutenticada = new UsernamePasswordAuthenticationToken("test@gmail.com", null);

        assertThat(profileSecurity.isOwner(null, authenticationOf("test@gmail.com"))).isFalse();
        assertThat(profileSecurity.isOwner("user-1", null)).isFalse();
        assertThat(profileSecurity.isOwner("user-1", noAutenticada)).isFalse();
        verifyNoInteractions(userRepository);
    }
}
