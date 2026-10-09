package com.anabada.fleaflea.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class NotificationAsyncConfig implements AsyncConfigurer {

    @Bean("notificationSseExecutor")
    public ThreadPoolTaskExecutor notificationSseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("notification-sse-");
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setRejectedExecutionHandler((task, pool) -> log.atWarn()
                .addKeyValue("queueSize", pool.getQueue().size())
                .addKeyValue("activeCount", pool.getActiveCount())
                .addKeyValue("shutdown", pool.isShutdown())
                .log("notification_sse_task_rejected"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> log.atError()
                .addKeyValue("method", method.getName())
                .setCause(throwable)
                .log("async_task_failed");
    }
}
