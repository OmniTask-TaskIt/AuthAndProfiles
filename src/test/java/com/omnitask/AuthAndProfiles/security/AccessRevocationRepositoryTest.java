package com.omnitask.AuthAndProfiles.security;

import com.omnitask.AuthAndProfiles.domain.ports.out.redis.AccessRevocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessRevocationRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AccessRevocationRepository accessRevocationRepository;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void revokeUser_deberiaGuardarLaMarcaConElTtlIndicado() {
        accessRevocationRepository.revokeUser("test@gmail.com", 900000L);

        verify(valueOperations).set("revoked_user:test@gmail.com", "REVOKED", Duration.ofMillis(900000L));
    }

    @Test
    void isUserRevoked_deberiaRetornarTrue_cuandoExisteLaMarca() {
        when(redisTemplate.hasKey("revoked_user:test@gmail.com")).thenReturn(true);

        assertThat(accessRevocationRepository.isUserRevoked("test@gmail.com")).isTrue();
    }

    @Test
    void isUserRevoked_deberiaRetornarFalse_cuandoNoExisteLaMarcaONoHayRespuesta() {
        when(redisTemplate.hasKey("revoked_user:test@gmail.com")).thenReturn(false);
        when(redisTemplate.hasKey("revoked_user:otro@gmail.com")).thenReturn(null);

        assertThat(accessRevocationRepository.isUserRevoked("test@gmail.com")).isFalse();
        assertThat(accessRevocationRepository.isUserRevoked("otro@gmail.com")).isFalse();
    }

    @Test
    void clearUserRevocation_deberiaEliminarLaMarca() {
        accessRevocationRepository.clearUserRevocation("test@gmail.com");

        verify(redisTemplate).delete("revoked_user:test@gmail.com");
    }
}
