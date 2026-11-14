package com.anabada.fleaflea.domain.notification.service;

import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.domain.PushPlatform;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceLimitExceededException;
import com.anabada.fleaflea.domain.notification.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushDeviceRegistrationService {

    private static final int MAX_DEVICES_PER_MEMBER = 10;

    private final PushDeviceRepository pushDeviceRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long registerPushDevice(
            Long memberId,
            String fid,
            PushPlatform platform
    ) {
        // 등록 수 검사와 저장을 직렬화하고 로그아웃과의 기존 잠금 순서를 유지한다.
        memberRepository.findLockedById(memberId).orElseThrow(MemberNotFoundException::new);

        if (!pushDeviceRepository.existsByFidAndMember_MemberId(fid, memberId)
                && pushDeviceRepository.countByMember_MemberId(memberId) >= MAX_DEVICES_PER_MEMBER) {
            throw new PushDeviceLimitExceededException();
        }

        pushDeviceRepository.upsert(memberId, fid, platform.name(), UUID.randomUUID());

        return pushDeviceRepository.findLockedByFid(fid)
                .orElseThrow()
                .getPushDeviceId();
    }
}
