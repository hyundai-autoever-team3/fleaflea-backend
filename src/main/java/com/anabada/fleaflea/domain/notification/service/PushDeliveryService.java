package com.anabada.fleaflea.domain.notification.service;

import com.anabada.fleaflea.domain.notification.domain.PushSendStatus;
import com.anabada.fleaflea.domain.notification.push.PushMessage;
import com.anabada.fleaflea.domain.notification.push.FirebasePushSender;
import com.anabada.fleaflea.domain.notification.push.PushSendResult;
import com.anabada.fleaflea.domain.notification.push.PushTarget;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushDeliveryService {

    private final PushDeviceService pushDeviceService;
    private final FirebasePushSender pushSender;
    private final MeterRegistry meterRegistry;

    public void sendPushMessage(
            Long receiverId,
            PushMessage pushMessage
    ) {
        for (PushTarget pushTarget : pushDeviceService.findPushTargets(receiverId)) {
            sendToDevice(pushTarget, pushMessage);
        }
    }

    private void sendToDevice(
            PushTarget pushTarget,
            PushMessage pushMessage
    ) {
        try {
            PushSendResult pushSendResult = pushSender.send(pushTarget, pushMessage);

            recordSendResult(pushMessage, pushSendResult);

            if (pushSendResult.status() == PushSendStatus.UNREGISTERED) {
                int deletedCount = pushDeviceService.deleteUnregisteredPushDevice(pushTarget);

                meterRegistry.counter("notification.push.registration.removed").increment(deletedCount);
            }
        } catch (RuntimeException exception) {
            // 외부 연동 및 DB 예외 원문에는 설치 식별자나 메시지가 포함될 수 있다.
            meterRegistry.counter(
                    "notification.push.send",
                    "category", pushMessage.category().name(),
                    "status", "FAILED"
            ).increment();

            log.atWarn()
                    .addKeyValue("category", pushMessage.category().name())
                    .addKeyValue("errorType", exception.getClass().getSimpleName())
                    .log("notification_push_failed");
        }
    }

    private void recordSendResult(
            PushMessage pushMessage,
            PushSendResult pushSendResult
    ) {
        meterRegistry.counter(
                "notification.push.send",
                "category", pushMessage.category().name(),
                "status", pushSendResult.status().name()
        ).increment();

        if (pushSendResult.status() != PushSendStatus.DISABLED) {
            String errorCode = pushSendResult.errorCode() != null
                    ? pushSendResult.errorCode().name()
                    : pushSendResult.status() == PushSendStatus.ACCEPTED ? "NONE" : "UNKNOWN";

            log.atInfo()
                    .addKeyValue("category", pushMessage.category().name())
                    .addKeyValue("status", pushSendResult.status())
                    .addKeyValue("errorCode", errorCode)
                    .log("notification_push_result");
        }
    }
}
