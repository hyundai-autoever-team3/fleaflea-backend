package com.anabada.fleaflea.domain.notification.sse;

import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
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
    private final MemberRepository memberRepository;

    public SseEmitter subscribe(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new MemberNotFoundException();
        }
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
        sendEvent(receiverId, EVENT_NOTIFICATION, notification);
    }

    public void sendEvent(Long memberId, String eventName, Object payload) {
        if (!memberRepository.existsById(memberId)) {
            closeMemberConnections(memberId);
            return;
        }
        for (Map.Entry<String, SseEmitter> entry : emitterRepository.findAllByMemberId(memberId)) {
            send(memberId, entry.getKey(), entry.getValue(), eventName, payload);
        }
    }

    public void sendHeartbeat() {
        emitterRepository.findAll().forEach(entry -> {
            Long memberId = entry.getKey();

            if (!memberRepository.existsById(memberId)) {
                closeMemberConnections(memberId);
                return;
            }

            String emitterId = entry.getValue().getKey();
            SseEmitter emitter = entry.getValue().getValue();

            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                emitterRepository.delete(memberId, emitterId);
            }
        });
    }

    public void closeMemberConnections(Long memberId) {
        for (Map.Entry<String, SseEmitter> entry
                : emitterRepository.findAllByMemberId(memberId)) {
            emitterRepository.delete(memberId, entry.getKey());

            try {
                entry.getValue().complete();
            } catch (RuntimeException exception) {
                log.warn(
                        "탈퇴 회원 SSE 종료 실패 : memberId = {}, emitterId = {}",
                        memberId,
                        entry.getKey(),
                        exception
                );
            }
        }
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

            log.atDebug()
                    .addKeyValue("event", eventName)
                    .addKeyValue("exceptionType", e.getClass().getSimpleName())
                    .log("sse_send_failed");
        }
    }

}
