package com.anabada.fleaflea.domain.poke.ratelimit;

import com.anabada.fleaflea.domain.poke.exception.PokeLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class PokeRateLimiter {

    static final int DAILY_LIMIT = 5;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;
    private final Cache<PokeLimitKey, Integer> counts;

    public PokeRateLimiter() {
        this(
                Clock.system(KOREA_ZONE),
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofDays(2))
                        .build()
        );
    }

    public void acquire(Long senderId, Long recipientId) {
        PokeLimitKey key = new PokeLimitKey(
                senderId,
                recipientId,
                LocalDate.now(clock)
        );
        AtomicBoolean acquired = new AtomicBoolean(false);

        counts.asMap().compute(key, (ignored, currentCount) -> {
            int count = currentCount == null ? 0 : currentCount;
            if (count >= DAILY_LIMIT) {
                return count;
            }
            acquired.set(true);
            return count + 1;
        });

        if (!acquired.get()) {
            throw new PokeLimitExceededException();
        }

        registerRollbackRelease(key);
    }

    private void registerRollbackRelease(PokeLimitKey key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    release(key);
                }
            }
        });
    }

    private void release(PokeLimitKey key) {
        counts.asMap().computeIfPresent(key, (ignored, currentCount) ->
                currentCount <= 1 ? null : currentCount - 1
        );
    }

    record PokeLimitKey(Long senderId, Long recipientId, LocalDate date) {
    }
}
