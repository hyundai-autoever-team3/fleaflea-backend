package com.anabada.fleaflea.domain.notification.sse;

import com.anabada.fleaflea.domain.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSseService {

    private static final long TIMEOUT_MILLIS = 30L * 60 * 1000;

    private static final String EVENT_CONNECT = "connect";
    private static final String EVENT_NOTIFICATION = "notification";

    private final SseEmitterRepository emitterRepository;

    public SseEmitter subscribe(Long memberId) {
        String emitterId = memberId + "_" + UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);

        emitterRepository.save(memberId, emitterId, emitter);

        emitter.onCompletion(() -> emitterRepository.delete(memberId, emitterId));
        emitter.onTimeout(() -> {
            emitterRepository.delete(memberId, emitterId);
            emitter.complete();
        });
        emitter.onError(throwable -> emitterRepository.delete(memberId, emitterId));

        send(memberId, emitterId, emitter, EVENT_CONNECT, "connected");

        return emitter;
    }

    public void sendNotification(
            Long receiverId,
            NotificationResponse notification
    ) {
        for (Map.Entry<String, SseEmitter> entry : emitterRepository.findAllByMemberId(receiverId)) {
            send(receiverId, entry.getKey(), entry.getValue(), EVENT_NOTIFICATION, notification);
        }
    }

    public void sendHeartbeat() {
        emitterRepository.findAll().forEach(entry -> {
            Long memberId = entry.getKey();
            String emitterId = entry.getValue().getKey();
            SseEmitter emitter = entry.getValue().getValue();

            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                emitterRepository.delete(memberId, emitterId);
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
        } catch (IOException | IllegalStateException e) {
            emitterRepository.delete(memberId, emitterId);
            log.debug("SSE 전송 실패 - memberId={}, event={}", memberId, eventName, e);
        }
    }
}
