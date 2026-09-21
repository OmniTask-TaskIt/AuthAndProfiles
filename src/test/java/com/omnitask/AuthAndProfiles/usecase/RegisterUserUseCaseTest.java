package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.domain.events.UserRegisteredEvent;
import com.omnitask.AuthAndProfiles.domain.events.EventType;
import com.omnitask.AuthAndProfiles.domain.ports.out.events.EventPublisher;
import com.omnitask.AuthAndProfiles.domain.enums.VerificationStatus;
import com.omnitask.AuthAndProfiles.application.services.OtpGenerator;
import com.omnitask.AuthAndProfiles.application.usecases.RegisterUserUseCase;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.enums.Role;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.RegisterRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.ProfileRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.resend.ResendEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
