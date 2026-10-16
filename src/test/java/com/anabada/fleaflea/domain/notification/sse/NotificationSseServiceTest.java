package com.anabada.fleaflea.domain.notification.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotificationSseServiceTest {

    private final SseEmitterRepository repository = new SseEmitterRepository();
    private final NotificationSseService service = new NotificationSseService(repository);

    @Test
    @DisplayName("구독 연결은 토큰 만료 시각을 전달받지 않고 최대 30분 동안 유지한다")
    void subscribe_usesFixedThirtyMinuteTimeout() {
        SseEmitter emitter = service.subscribe(1L);

        assertThat(emitter.getTimeout()).isEqualTo(30L * 60 * 1000);
        assertThat(repository.findAllByMemberId(1L)).hasSize(1);
    }

    @Test
    @DisplayName("등록된 연결에 토큰 재검증 없이 알림을 전송한다")
    void sendEvent_sendsToRegisteredConnection() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        repository.save(1L, "active", emitter);

        service.sendEvent(1L, "notification", "message");

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, never()).complete();
    }

    @Test
    @DisplayName("조회 직후 로그아웃으로 제거된 연결에는 알림을 전송하지 않는다")
    void sendEvent_skipsRemovedConnection() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitterRepository connections = mock(SseEmitterRepository.class);
        when(connections.findAllByMemberId(1L)).thenReturn(List.of(Map.entry("removed", emitter)));

        new NotificationSseService(connections).sendEvent(1L, "notification", "message");

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    @DisplayName("로그아웃 시 회원의 모든 탭을 닫되 다른 회원의 연결은 유지한다")
    void disconnectMember_closesOnlyMemberConnections() {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        SseEmitter other = mock(SseEmitter.class);
        repository.save(1L, "first", first);
        repository.save(1L, "second", second);
        repository.save(2L, "other", other);

        service.disconnectMember(1L);

        verify(first).complete();
        verify(second).complete();
        verify(other, never()).complete();
        assertThat(repository.findAllByMemberId(1L)).isEmpty();
        assertThat(repository.findAllByMemberId(2L)).hasSize(1);
    }
}
