package com.gameexpert.chat.service;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatRateLimitService {

    private final StringRedisTemplate redisTemplate;

    public boolean allow(Long playerId) {
        String key = "chat:limit:" + playerId;
        String value = redisTemplate.opsForValue().get(key);
        int count = value == null ? 0 : Integer.parseInt(value);
        if (count >= 5) {
            return false;
        }
        // TODO Lv 19: 횟수 확인부터 최초 만료 설정까지 원자적으로 실행합니다.
        DefaultRedisScript<Long> INCREASE_SCRIPT = new DefaultRedisScript<>("""
        local limit = tonumber(ARGV[1])
        local ttl   = tonumber(ARGV[2])

        local current = tonumber(redis.call('GET', KEYS[1]) or '0')
        if current >= limit then
        return 0
        end

        local updated = redis.call('INCR', KEYS[1])
        if updated == 1 then
        redis.call('EXPIRE', KEYS[1], ttl)
        end
        return 1
            """, Long.class);
        Long updated = redisTemplate.execute(INCREASE_SCRIPT, List.of(key), "5", "10");
        if (updated == 1L) {
            return true;
        }
        return false;
    }
}
