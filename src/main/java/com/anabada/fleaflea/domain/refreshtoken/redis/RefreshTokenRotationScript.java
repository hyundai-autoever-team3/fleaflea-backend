package com.anabada.fleaflea.domain.refreshtoken.redis;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenRotationScript extends DefaultRedisScript<Long> {

    public RefreshTokenRotationScript() {
        super("""
                if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                    return 0
                end

                redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                return 1
                """, Long.class);
    }
}
