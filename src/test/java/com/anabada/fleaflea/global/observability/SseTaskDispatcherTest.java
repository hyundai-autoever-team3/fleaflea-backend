package com.anabada.fleaflea.global.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class SseTaskDispatcherTest {

    @Test
    void rejectedTaskIsCountedAndDoesNotEscapeToCaller() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        executor.shutdown();

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SseTaskDispatcher dispatcher = new SseTaskDispatcher(
                executor,
                meterRegistry,
                ObservationRegistry.NOOP
        );

        dispatcher.submit(SseTaskType.CHAT, () -> {
            throw new AssertionError("rejected task must not run");
        });

        assertThat(meterRegistry.counter(
                "fleaflea.sse.tasks",
                "type", "chat",
                "outcome", "submitted"
        ).count()).isEqualTo(1);
        assertThat(meterRegistry.counter(
                "fleaflea.sse.tasks",
                "type", "chat",
                "outcome", "rejected"
        ).count()).isEqualTo(1);
    }
}
