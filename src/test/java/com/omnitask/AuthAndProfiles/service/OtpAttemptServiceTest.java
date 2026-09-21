package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.OtpAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OtpAttemptService otpAttemptService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void registerFailureAndCheckLimit_deberiaFijarExpiracionYNoBloquear_enElPrimerIntento() {
        when(valueOperations.increment("otp_attempts:test@gmail.com")).thenReturn(1L);

        boolean limitReached = otpAttemptService.registerFailureAndCheckLimit("test@gmail.com");

        assertThat(limitReached).isFalse();
        verify(redisTemplate).expire("otp_attempts:test@gmail.com", 600L, TimeUnit.SECONDS);
    }

    @Test
    void registerFailureAndCheckLimit_noDeberiaExpirarNiBloquear_enIntentosIntermedios() {
        when(valueOperations.increment("otp_attempts:test@gmail.com")).thenReturn(3L);

        boolean limitReached = otpAttemptService.registerFailureAndCheckLimit("test@gmail.com");

        assertThat(limitReached).isFalse();
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    void registerFailureAndCheckLimit_deberiaIndicarLimiteAlcanzado_enElQuintoIntento() {
        when(valueOperations.increment("otp_attempts:test@gmail.com")).thenReturn(5L);

        boolean limitReached = otpAttemptService.registerFailureAndCheckLimit("test@gmail.com");

        assertThat(limitReached).isTrue();
    }

    @Test
    void registerFailureAndCheckLimit_noDeberiaBloquear_cuandoRedisRetornaNull() {
        when(valueOperations.increment("otp_attempts:test@gmail.com")).thenReturn(null);

        boolean limitReached = otpAttemptService.registerFailureAndCheckLimit("test@gmail.com");

        assertThat(limitReached).isFalse();
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    void reset_deberiaEliminarElContador() {
        otpAttemptService.reset("test@gmail.com");

        verify(redisTemplate).delete("otp_attempts:test@gmail.com");
    }
}
