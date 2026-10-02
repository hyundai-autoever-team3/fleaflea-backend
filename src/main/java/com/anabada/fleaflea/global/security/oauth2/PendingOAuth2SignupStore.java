package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.global.security.oauth2.dto.PendingOAuth2Signup;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class PendingOAuth2SignupStore {

    private static final Duration EXPIRATION =
            Duration.ofMinutes(10);

    private final Cache<String, PendingOAuth2Signup> cache =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(EXPIRATION)
                    .build();

    public String save(
            com.anabada.fleaflea.domain.member.dto.OAuth2MemberInfo memberInfo
    ) {
        String ticket = UUID.randomUUID().toString();

        PendingOAuth2Signup pendingSignup =
                new PendingOAuth2Signup(
                        memberInfo,
                        LocalDateTime.now().plus(EXPIRATION)
                );

        cache.put(ticket, pendingSignup);

        return ticket;
    }

    public Optional<PendingOAuth2Signup> consume(
            String ticket
    ) {
        PendingOAuth2Signup pendingSignup =
                cache.asMap().remove(ticket);

        if (pendingSignup == null
                || pendingSignup.isExpired()) {
            return Optional.empty();
        }

        return Optional.of(pendingSignup);
    }
}