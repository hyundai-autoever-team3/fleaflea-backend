package com.anabada.fleaflea.domain.notification.service;

import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.domain.PushPlatform;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceLimitExceededException;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceRegistrationRateLimitExceededException;
import com.anabada.fleaflea.domain.notification.ratelimit.PushDeviceRegistrationRateLimiter;
import com.anabada.fleaflea.domain.notification.repository.PushDeviceRepository;
import com.anabada.fleaflea.fixture.MemberFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PushDeviceServiceTest {

    @Test
    @DisplayName("등록 요청 제한을 넘으면 DB 조회나 회원 잠금을 시도하지 않는다")
    void register_rejectsRateLimitBeforeDatabaseAccess() {
        PushDeviceRepository devices = mock(PushDeviceRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PushDeviceRegistrationRateLimiter limiter = mock(PushDeviceRegistrationRateLimiter.class);

        doThrow(new PushDeviceRegistrationRateLimitExceededException()).when(limiter).acquire(1L);

        PushDeviceService service = new PushDeviceService(
                devices,
                limiter,
                new PushDeviceRegistrationService(devices, members)
        );

        assertThatThrownBy(() -> service.registerPushDevice(1L, "c1234567890123456789012", PushPlatform.WEB))
                .isInstanceOf(PushDeviceRegistrationRateLimitExceededException.class);

        verifyNoInteractions(devices, members);
    }

    @Test
    @DisplayName("기기 수 초과로 실패한 등록도 요청 횟수에 포함한다")
    void register_countsFailedAttemptsTowardsRateLimit() {
        PushDeviceRepository devices = mock(PushDeviceRepository.class);
        MemberRepository members = mock(MemberRepository.class);

        when(members.findLockedById(1L)).thenReturn(Optional.of(MemberFixture.createMember(1L)));
        when(devices.countByMember_MemberId(1L)).thenReturn(10L);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AtomicInteger attempts = new AtomicInteger();

        when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString(), anyString(), anyString()))
                .thenAnswer(ignored -> attempts.incrementAndGet() <= 10 ? 1L : 0L);

        PushDeviceService service = new PushDeviceService(
                devices,
                new PushDeviceRegistrationRateLimiter(redis),
                new PushDeviceRegistrationService(devices, members)
        );

        for (int index = 0; index < 10; index++) {
            assertThatThrownBy(() -> service.registerPushDevice(1L, "c1234567890123456789012", PushPlatform.WEB))
                    .isInstanceOf(PushDeviceLimitExceededException.class);
        }

        assertThatThrownBy(() -> service.registerPushDevice(1L, "c1234567890123456789012", PushPlatform.WEB))
                .isInstanceOf(PushDeviceRegistrationRateLimitExceededException.class);

        verify(members, times(10)).findLockedById(1L);
        verify(devices, never()).upsert(any(), any(), any(), any());
    }
}
