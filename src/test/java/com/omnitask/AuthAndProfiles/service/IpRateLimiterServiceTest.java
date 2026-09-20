package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.IpRateLimiterService;

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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpRateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private IpRateLimiterService ipRateLimiterService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void isBlocked_deberiaRetornarTrue_cuandoLaIpTieneUnaLlaveDeBloqueo() {
        // Arrange
        when(valueOperations.get("block_ip:127.0.0.1")).thenReturn("BLOCKED");

        // Act
        boolean blocked = ipRateLimiterService.isBlocked("127.0.0.1");

        // Assert
        assertThat(blocked).isTrue();
    }

    @Test
    void isBlocked_deberiaRetornarFalse_cuandoNoHayLlaveDeBloqueo() {
        // Arrange
        when(valueOperations.get("block_ip:127.0.0.1")).thenReturn(null);

        // Act
        boolean blocked = ipRateLimiterService.isBlocked("127.0.0.1");

        // Assert
        assertThat(blocked).isFalse();
    }

    @Test
    void recordFailedAttempt_deberiaBloquearLaIp_cuandoSeAlcanzaElMaximoDeIntentos() {
        // Arrange
        when(valueOperations.increment("attempts_ip:127.0.0.1")).thenReturn(5L);

        // Act
        ipRateLimiterService.recordFailedAttempt("127.0.0.1");

        // Assert
        verify(valueOperations).set(eq("block_ip:127.0.0.1"), eq("BLOCKED"), eq(900L), eq(TimeUnit.SECONDS));
        verify(redisTemplate).delete("attempts_ip:127.0.0.1");
    }

    @Test
    void recordFailedAttempt_soloDeberiaFijarExpiracion_enElPrimerIntento() {
        // Arrange
        when(valueOperations.increment("attempts_ip:127.0.0.1")).thenReturn(1L);

        // Act
        ipRateLimiterService.recordFailedAttempt("127.0.0.1");

        // Assert
        verify(redisTemplate).expire("attempts_ip:127.0.0.1", 900L, TimeUnit.SECONDS);
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void resetAttempts_deberiaEliminarLaLlaveDeIntentos() {
        // Act
        ipRateLimiterService.resetAttempts("127.0.0.1");

        // Assert
        verify(redisTemplate).delete("attempts_ip:127.0.0.1");
    }
}
