package com.anabada.fleaflea.domain.notification.service;

import com.anabada.fleaflea.domain.notification.domain.PushSendStatus;
import com.google.firebase.messaging.MessagingErrorCode;
import com.anabada.fleaflea.domain.notification.push.PushMessage;
import com.anabada.fleaflea.domain.notification.push.ChatMessagePushData;
import com.anabada.fleaflea.domain.notification.push.FirebasePushSender;
import com.anabada.fleaflea.domain.notification.push.PushSendResult;
import com.anabada.fleaflea.domain.notification.push.PushTarget;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PushDeliveryServiceTest {

    private final PushDeviceService devices = mock(PushDeviceService.class);
    private final FirebasePushSender sender = mock(FirebasePushSender.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PushDeliveryService service = new PushDeliveryService(devices, sender, registry);
    private final PushMessage message = PushMessage.of("title", "preview", ChatMessagePushData.of(1L, 2L));

    @Test
    @DisplayName("등록 기기가 없는 회원에게는 외부 전송을 호출하지 않는다")
    void send_withoutDevices_doesNotCallProvider() {
        when(devices.findPushTargets(1L)).thenReturn(List.of());

        service.sendPushMessage(1L, message);

        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("일부 기기가 실패해도 나머지 기기에 전송하고 무효 등록만 정리한다")
    void send_partialFailures_doesNotStopOtherDevices() {
        PushTarget first = target(1L),
                second = target(2L),
                third = target(3L),
                fourth = target(4L);

        when(devices.findPushTargets(1L)).thenReturn(List.of(first, second, third, fourth));
        when(sender.send(first, message)).thenThrow(new IllegalStateException("private provider data"));
        when(sender.send(second, message))
                .thenReturn(PushSendResult.of(PushSendStatus.FAILED, MessagingErrorCode.INVALID_ARGUMENT));
        when(sender.send(third, message))
                .thenReturn(PushSendResult.of(PushSendStatus.UNREGISTERED, MessagingErrorCode.UNREGISTERED));
        when(sender.send(fourth, message)).thenReturn(PushSendResult.of(PushSendStatus.ACCEPTED, null));
        when(devices.deleteUnregisteredPushDevice(third)).thenReturn(1);

        service.sendPushMessage(1L, message);

        verify(sender).send(fourth, message);
        verify(devices).deleteUnregisteredPushDevice(third);
        verify(devices, never()).deleteUnregisteredPushDevice(first);
        verify(devices, never()).deleteUnregisteredPushDevice(second);
        assertThat(registry.get("notification.push.send").tag("status", "FAILED").counter().count())
                .isEqualTo(2);
        assertThat(registry.get("notification.push.registration.removed").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("오류 코드가 없어도 성공과 실패를 정확히 기록하고 기기를 삭제하지 않는다")
    void send_missingErrorCodePreservesStatus() {
        PushTarget first = target(1L),
                second = target(2L);

        when(devices.findPushTargets(1L)).thenReturn(List.of(first, second));
        when(sender.send(first, message)).thenReturn(PushSendResult.of(PushSendStatus.ACCEPTED, null));
        when(sender.send(second, message)).thenReturn(PushSendResult.of(PushSendStatus.FAILED, null));

        service.sendPushMessage(1L, message);

        assertThat(registry.get("notification.push.send").tag("status", "ACCEPTED").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("notification.push.send").tag("status", "FAILED").counter().count())
                .isEqualTo(1);
        verify(devices, never()).deleteUnregisteredPushDevice(any());
    }

    private PushTarget target(Long id) {
        return PushTarget.of(id, 1L, "test-installation-" + id, UUID.randomUUID());
    }
}
