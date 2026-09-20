package com.omnitask.AuthAndProfiles.usecase;

import com.omnitask.AuthAndProfiles.application.usecases.VerifyOtpUseCase;

import com.omnitask.AuthAndProfiles.domain.enums.AccountStatus;
import com.omnitask.AuthAndProfiles.domain.models.User;
import com.omnitask.AuthAndProfiles.domain.ports.out.redis.TokenRedisRepository;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.in.web.dto.VerifyOtpRequestDTO;
import com.omnitask.AuthAndProfiles.infrastructure.adapters.out.mongo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerifyOtpUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenRedisRepository tokenRedisRepository;

    @InjectMocks
    private VerifyOtpUseCase verifyOtpUseCase;

    private VerifyOtpRequestDTO request;

    @BeforeEach
    void setUp() {
        request = new VerifyOtpRequestDTO();
        request.setEmail("test@gmail.com");
        request.setOtpCode("123456");
    }

    @Test
    void execute_deberiaActivarLaCuenta_cuandoElOtpEsValido() {
        // Arrange
        User user = User.builder().email("test@gmail.com").build();
        when(tokenRedisRepository.getRefreshToken("otp:test@gmail.com")).thenReturn("123456");
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        // Act
        verifyOtpUseCase.execute(request);

        // Assert
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userRepository).save(user);
        verify(tokenRedisRepository).saveRefreshToken("otp:test@gmail.com", "", 1);
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElOtpNoCoincide() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("otp:test@gmail.com")).thenReturn("000000");

        // Act & Assert
        assertThatThrownBy(() -> verifyOtpUseCase.execute(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Código OTP inválido");
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_deberiaLanzarExcepcion_cuandoElOtpHaExpirado() {
        // Arrange
        when(tokenRedisRepository.getRefreshToken("otp:test@gmail.com")).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> verifyOtpUseCase.execute(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Código OTP inválido");
    }
}
