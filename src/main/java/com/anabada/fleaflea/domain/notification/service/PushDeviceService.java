package com.anabada.fleaflea.domain.notification.service;

import com.anabada.fleaflea.domain.notification.domain.PushDevice;
import com.anabada.fleaflea.domain.notification.domain.PushPlatform;
import com.anabada.fleaflea.domain.notification.ratelimit.PushDeviceRegistrationRateLimiter;
import com.anabada.fleaflea.domain.notification.push.PushTarget;
import com.anabada.fleaflea.domain.notification.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PushDeviceService {

    private final PushDeviceRepository pushDeviceRepository;
    private final PushDeviceRegistrationRateLimiter registrationRateLimiter;
    private final PushDeviceRegistrationService pushDeviceRegistrationService;

    public Long registerPushDevice(
            Long memberId,
            String fid,
            PushPlatform platform
    ) {
        registrationRateLimiter.acquire(memberId);

        return pushDeviceRegistrationService.registerPushDevice(memberId, fid, platform);
    }

    @Transactional
    public void deletePushDevice(
            Long memberId,
            Long deviceId
    ) {
        PushDevice pushDevice = pushDeviceRepository.findLockedByDeviceId(deviceId)
                .orElse(null);

        if (pushDevice == null) {
            return;
        }

        pushDevice.validateOwner(memberId);

        pushDeviceRepository.delete(pushDevice);
    }

    @Transactional(readOnly = true)
    public List<PushTarget> findPushTargets(Long memberId) {
        return pushDeviceRepository.findTargets(memberId);
    }

    @Transactional
    public int deleteUnregisteredPushDevice(PushTarget pushTarget) {
        return pushDeviceRepository.deleteUnregistered(
                pushTarget.pushDeviceId(),
                pushTarget.memberId(),
                pushTarget.registrationVersion()
        );
    }
}
