package com.anabada.fleaflea.global.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationAsyncConfigTest {

    @Test
    @DisplayName("전송 실행기 큐가 포화돼도 요청 스레드에서 실행하지 않고 거절 지표를 남긴다")
    void executor_rejectionIsCountedWithoutBlockingCaller() throws Exception {
        var registry = new SimpleMeterRegistry();
        var executor = new NotificationAsyncConfig().notificationPushExecutor(registry);

        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            executor.execute(() -> {
                entered.countDown();

                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });

            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();

            executor.execute(() -> {});
            executor.execute(() -> {
                throw new AssertionError("rejected task must not run");
            });

            assertThat(registry.get("notification.push.task.rejected").counter().count()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
