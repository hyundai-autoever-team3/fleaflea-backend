package com.anabada.fleaflea.domain.notification.sse;

import com.anabada.fleaflea.domain.notification.dto.NotificationResponse;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class NotificationSseService {

    private static final long TIMEOUT_MILLIS = 30L * 60 * 1000;

    private static final String EVENT_CONNECT = "connect";
    private static final String EVENT_NOTIFICATION = "notification";

    private final SseEmitterRepository emitterRepository;
    private final MeterRegistry meterRegistry;

    public NotificationSseService(
            SseEmitterRepository emitterRepository,
            MeterRegistry meterRegistry
    ) {
        this.emitterRepository = emitterRepository;
        this.meterRegistry = meterRegistry;
        Gauge.builder(
                        "fleaflea.sse.connections",
                        emitterRepository,
                        SseEmitterRepository::countConnections
                )
                .description("Current SSE connections")
                .register(meterRegistry);
    }

    public SseEmitter subscribe(Long memberId) {
        String emitterId = memberId + "_" + UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);

        emitterRepository.save(memberId, emitterId, emitter);

        emitter.onCompletion(() -> remove(memberId, emitterId, "completed"));
        emitter.onTimeout(() -> {
            remove(memberId, emitterId, "timeout");
            emitter.complete();
        });
        emitter.onError(throwable -> remove(memberId, emitterId, "error"));

        send(memberId, emitterId, emitter, EVENT_CONNECT, "connected");

        return emitter;
    }

    public void sendNotification(
            Long receiverId,
            NotificationResponse notification
    ) {
        sendEvent(receiverId, EVENT_NOTIFICATION, notification);
    }

    public void sendEvent(Long memberId, String eventName, Object payload) {
        for (Map.Entry<String, SseEmitter> entry : emitterRepository.findAllByMemberId(memberId)) {
            send(memberId, entry.getKey(), entry.getValue(), eventName, payload);
        }
    }

    public void sendHeartbeat() {
        emitterRepository.findAll().forEach(entry -> {
            Long memberId = entry.getKey();
            String emitterId = entry.getValue().getKey();
            SseEmitter emitter = entry.getValue().getValue();

            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
                countSend("heartbeat", "success");
            } catch (IOException | IllegalStateException e) {
                remove(memberId, emitterId, "send_failure");
                countSend("heartbeat", "failure");
            }
        });
    }

    private void send(
            Long memberId,
            String emitterId,
            SseEmitter emitter,
            String eventName,
            Object data
    ) {
        try {
            emitter.send(SseEmitter.event()
                    .id(emitterId)
                    .name(eventName)
                    .data(data));
            countSend(eventName, "success");
        } catch (IOException | IllegalStateException e) {
            remove(memberId, emitterId, "send_failure");
            countSend(eventName, "failure");
            log.debug("SSE 전송 실패 - event={}", normalizedEvent(eventName), e);
        }
    }

    private void remove(Long memberId, String emitterId, String outcome) {
        if (emitterRepository.delete(memberId, emitterId)) {
            meterRegistry.counter("fleaflea.sse.connections.closed", "outcome", outcome)
                    .increment();
        }
    }

    private void countSend(String eventName, String outcome) {
        meterRegistry.counter(
                "fleaflea.sse.sends",
                "event", normalizedEvent(eventName),
                "outcome", outcome
        ).increment();
    }

    private String normalizedEvent(String eventName) {
        return switch (eventName) {
            case EVENT_CONNECT -> "connect";
            case EVENT_NOTIFICATION -> "notification";
            case "chat-message" -> "chat-message";
            case "chat-read" -> "chat-read";
            case "heartbeat" -> "heartbeat";
            default -> "other";
        };
    }
}
