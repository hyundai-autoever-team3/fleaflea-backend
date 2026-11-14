package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.member.dto.LogoutRequest;
import com.anabada.fleaflea.domain.member.event.MemberLoggedOutEvent;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.member.repository.RedisEmailVerificationRepository;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceNotOwnerException;
import com.anabada.fleaflea.domain.notification.service.PushDeviceService;
import com.anabada.fleaflea.domain.refreshtoken.repository.RedisRefreshTokenRepository;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberAuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RedisRefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenIssueService tokenIssueService;

    @Mock
    private RedisEmailVerificationRepository emailVerificationRepository;

    @Mock
    private PushDeviceService pushDeviceService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MemberAuthService service;

    @BeforeEach
    void setUp() {
        when(memberRepository.findLockedById(1L)).thenReturn(Optional.of(MemberFixture.createMember(1L)));
    }

    @Test
    @DisplayName("현재 기기를 해제한 후 기존 Refresh Token 무효화와 로그아웃 이벤트를 수행한다")
    void logout_removesCurrentDeviceBeforeRevokingAuthentication() {
        service.logout(1L, LogoutRequest.of(7L));

        var ordered = inOrder(pushDeviceService, refreshTokenRepository, eventPublisher);
        ordered.verify(pushDeviceService).deletePushDevice(1L, 7L);
        ordered.verify(refreshTokenRepository).deleteByMemberId(1L);
        ordered.verify(eventPublisher).publishEvent(MemberLoggedOutEvent.of(1L));
        verifyNoMoreInteractions(pushDeviceService);
    }

    @Test
    @DisplayName("기기 ID가 없으면 푸시 등록은 건드리지 않고 로그아웃한다")
    void logout_withoutDeviceIdPreservesPushRegistrations() {
        service.logout(1L, LogoutRequest.of(null));

        verifyNoInteractions(pushDeviceService);
        verify(refreshTokenRepository).deleteByMemberId(1L);
        verify(eventPublisher).publishEvent(MemberLoggedOutEvent.of(1L));
    }

    @Test
    @DisplayName("요청 본문이 없으면 푸시 등록은 건드리지 않고 로그아웃한다")
    void logout_withoutRequestPreservesPushRegistrations() {
        service.logout(1L, null);

        verifyNoInteractions(pushDeviceService);
        verify(refreshTokenRepository).deleteByMemberId(1L);
        verify(eventPublisher).publishEvent(MemberLoggedOutEvent.of(1L));
    }

    @Test
    @DisplayName("다른 회원의 기기라면 Refresh Token 삭제와 SSE 종료 이벤트를 수행하지 않는다")
    void logout_wrongOwnerDoesNotRevokeAuthentication() {
        doThrow(new PushDeviceNotOwnerException()).when(pushDeviceService).deletePushDevice(1L, 7L);

        assertThatThrownBy(() -> service.logout(1L, LogoutRequest.of(7L)))
                .isInstanceOf(PushDeviceNotOwnerException.class);

        verifyNoInteractions(refreshTokenRepository, eventPublisher);
    }
}
