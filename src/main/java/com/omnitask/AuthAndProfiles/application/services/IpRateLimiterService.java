package com.omnitask.AuthAndProfiles.application.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class IpRateLimiterService {

    private final StringRedisTemplate redisTemplate;

    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_TIME_SECONDS = 900;

    public boolean isBlocked(String clientIp) {
        String blockKey = "block_ip:" + clientIp;
        String blocked = redisTemplate.opsForValue().get(blockKey);
        return blocked != null;
    }

    public void recordFailedAttempt(String clientIp) {
        String attemptsKey = "attempts_ip:" + clientIp;

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, BLOCK_TIME_SECONDS, TimeUnit.SECONDS);
        }

        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            String blockKey = "block_ip:" + clientIp;
            redisTemplate.opsForValue().set(blockKey, "BLOCKED", BLOCK_TIME_SECONDS, TimeUnit.SECONDS);
            redisTemplate.delete(attemptsKey);
        }
    }

    public void resetAttempts(String clientIp) {
        String attemptsKey = "attempts_ip:" + clientIp;
        redisTemplate.delete(attemptsKey);
    }
}
