package com.anabada.fleaflea.domain.notification.sse;

import com.anabada.fleaflea.domain.refreshtoken.exception.InvalidTokenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NotificationSseServiceTest {

    private final SseEmitterRepository repository = new SseEmitterRepository();
    private final NotificationSseService service = new NotificationSseService(repository);

    @Test
    @DisplayName("연결 수명은 JWT의 남은 유효시간과 최대 30분 중 짧은 값으로 정한다")
    void subscribe_limitsTimeoutToTokenLifetime() {
        SseEmitter shortLived = service.subscribe(1L, Instant.now().plusSeconds(60));
        SseEmitter longLived = service.subscribe(2L, Instant.now().plusSeconds(3600));

        assertThat(shortLived.getTimeout()).isBetween(59_000L, 60_000L);
        assertThat(longLived.getTimeout()).isEqualTo(30L * 60 * 1000);
    }

    @Test
    @DisplayName("이미 만료된 토큰으로는 연결을 저장하지 않는다")
    void subscribe_rejectsExpiredToken() {
        assertThatThrownBy(() -> service.subscribe(1L, Instant.now().minusSeconds(1)))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(repository.countConnections()).isZero();
    }

    @Test
    @DisplayName("토큰이 만료된 연결에는 알림을 보내지 않고 즉시 닫는다")
    void sendEvent_closesExpiredConnectionBeforeSending() throws Exception {
        SseEmitter expired = mock(SseEmitter.class);

        repository.save(1L, "expired", expired, Instant.now().minusSeconds(1));

        service.sendEvent(1L, "notification", "private content");

        verify(expired).complete();
        verify(expired, never()).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(repository.countConnections()).isZero();
    }

    @Test
    @DisplayName("알림이 없는 연결도 만료 정리 대상이며 유효한 연결은 유지한다")
    void closeExpiredConnections_preservesActiveConnections() {
        SseEmitter expired = mock(SseEmitter.class);
        SseEmitter active = mock(SseEmitter.class);

        repository.save(1L, "expired", expired, Instant.now().minusSeconds(1));
        repository.save(2L, "active", active, Instant.now().plusSeconds(60));

        service.closeExpiredConnections();

        verify(expired).complete();
        verify(active, never()).complete();
        assertThat(repository.findAllByMemberId(1L)).isEmpty();
        assertThat(repository.findAllByMemberId(2L)).hasSize(1);
    }

    @Test
    @DisplayName("회원 연결을 종료하면 모든 탭을 닫되 다른 회원의 연결은 유지한다")
    void disconnectMember_closesOnlyMemberConnections() {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        SseEmitter other = mock(SseEmitter.class);
        Instant expiresAt = Instant.now().plusSeconds(60);

        repository.save(1L, "first", first, expiresAt);
        repository.save(1L, "second", second, expiresAt);
        repository.save(2L, "other", other, expiresAt);

        service.disconnectMember(1L);

        verify(first).complete();
        verify(second).complete();
        verify(other, never()).complete();
        assertThat(repository.findAllByMemberId(1L)).isEmpty();
        assertThat(repository.findAllByMemberId(2L)).hasSize(1);
    }
}
