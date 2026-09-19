package com.omnitask.AuthAndProfiles.domain.ports.out.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class TokenRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public void saveRefreshToken(String email, String refreshToken, long durationInMillis) {
        redisTemplate.opsForValue().set("refresh_token:" + email, refreshToken, Duration.ofMillis(durationInMillis));
    }

    public String getRefreshToken(String email) {
        return redisTemplate.opsForValue().get("refresh_token:" + email);
    }

    public void deleteRefreshToken(String email) {
        redisTemplate.delete("refresh_token:" + email);
    }
}
