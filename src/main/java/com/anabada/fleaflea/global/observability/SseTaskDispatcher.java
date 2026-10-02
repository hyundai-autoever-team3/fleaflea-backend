package com.anabada.fleaflea.global.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class SseTaskDispatcher {

    @Qualifier("notificationSseExecutor")
    private final ThreadPoolTaskExecutor executor;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public SseTaskDispatcher(
            @Qualifier("notificationSseExecutor") ThreadPoolTaskExecutor executor,
            MeterRegistry meterRegistry,
            ObservationRegistry observationRegistry
    ) {
        this.executor = executor;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    public void submit(SseTaskType type, Runnable task) {
        count(type, "submitted");
        long submittedAt = System.nanoTime();

        try {
            executor.execute(() -> run(type, task, submittedAt));
        } catch (TaskRejectedException e) {
            count(type, "rejected");
            log.warn("SSE 비동기 작업 버림 - type={}, queueSize={}, activeCount={}",
                    type.name().toLowerCase(),
                    executor.getThreadPoolExecutor().getQueue().size(),
                    executor.getActiveCount());
        }
    }

    private void run(SseTaskType type, Runnable task, long submittedAt) {
        Timer.builder("fleaflea.sse.task.queue.wait")
                .description("Time an SSE task waited before execution")
                .tag("type", type.name().toLowerCase())
                .register(meterRegistry)
                .record(Duration.ofNanos(System.nanoTime() - submittedAt));

        Observation observation = Observation.start("fleaflea.sse.task", observationRegistry)
                .lowCardinalityKeyValue("task.type", type.name().toLowerCase());

        try (Observation.Scope ignored = observation.openScope()) {
            task.run();
            count(type, "completed");
        } catch (RuntimeException e) {
            observation.error(e);
            count(type, "failed");
            log.error("SSE 비동기 작업 실패 - type={}", type.name().toLowerCase(), e);
        } finally {
            observation.stop();
        }
    }

    private void count(SseTaskType type, String outcome) {
        meterRegistry.counter(
                "fleaflea.sse.tasks",
                "type", type.name().toLowerCase(),
                "outcome", outcome
        ).increment();
    }
}
