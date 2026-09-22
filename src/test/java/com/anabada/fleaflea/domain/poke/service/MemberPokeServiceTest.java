package com.anabada.fleaflea.domain.poke.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.notifier.PokeNotifier;
import com.anabada.fleaflea.domain.poke.domain.MemberPoke;
import com.anabada.fleaflea.domain.poke.event.MemberPokedEvent;
import com.anabada.fleaflea.domain.poke.exception.PokeLimitExceededException;
import com.anabada.fleaflea.domain.poke.ratelimit.PokeRateLimiter;
import com.anabada.fleaflea.domain.poke.repository.MemberPokeRepository;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberPokeServiceTest {

    private static final Long SENDER_ID = 1L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long POKE_ID = 3L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberPokeRepository pokeRepository;

    @Mock
    private PokeNotifier pokeNotifier;

    @Mock
    private PokeRateLimiter pokeRateLimiter;

    @InjectMocks
    private MemberPokeService service;

    @Test
    @DisplayName("콕찌르기를 저장하고 수신자에게 알림을 전송한다")
    void sendPoke() {
        Member sender = MemberFixture.createMember(SENDER_ID);
        Member recipient = MemberFixture.createMember(RECIPIENT_ID);
        when(memberRepository.findById(SENDER_ID)).thenReturn(Optional.of(sender));
        when(memberRepository.findById(RECIPIENT_ID)).thenReturn(Optional.of(recipient));

        service.send(SENDER_ID, RECIPIENT_ID);

        ArgumentCaptor<MemberPoke> pokeCaptor = ArgumentCaptor.forClass(MemberPoke.class);
        verify(pokeRepository).save(pokeCaptor.capture());
        verify(pokeRateLimiter).acquire(SENDER_ID, RECIPIENT_ID);
        verify(pokeNotifier).notifyOf(any(MemberPokedEvent.class));

        MemberPoke savedPoke = pokeCaptor.getValue();
        assertThat(savedPoke.getSender()).isSameAs(sender);
        assertThat(savedPoke.getRecipient()).isSameAs(recipient);
        assertThat(savedPoke.isRead()).isFalse();
    }

    @Test
    @DisplayName("자기 자신에게는 콕찌르기를 보낼 수 없다")
    void cannotPokeSelf() {
        assertThatThrownBy(() -> service.send(SENDER_ID, SENDER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POKE_SELF));

        verifyNoInteractions(memberRepository, pokeRepository, pokeNotifier, pokeRateLimiter);
    }

    @Test
    @DisplayName("같은 수신자에 대한 일일 횟수를 초과하면 콕찌르기를 저장하지 않는다")
    void cannotPokeAfterExceedingDailyLimit() {
        Member sender = MemberFixture.createMember(SENDER_ID);
        Member recipient = MemberFixture.createMember(RECIPIENT_ID);
        when(memberRepository.findById(SENDER_ID)).thenReturn(Optional.of(sender));
        when(memberRepository.findById(RECIPIENT_ID)).thenReturn(Optional.of(recipient));
        doThrow(new PokeLimitExceededException())
                .when(pokeRateLimiter).acquire(SENDER_ID, RECIPIENT_ID);

        assertThatThrownBy(() -> service.send(SENDER_ID, RECIPIENT_ID))
                .isInstanceOf(PokeLimitExceededException.class);

        verify(pokeRepository, never()).save(any());
        verifyNoInteractions(pokeNotifier);
    }

    @Test
    @DisplayName("수신자는 콕찌르기를 읽음 처리할 수 있다")
    void recipientMarksPokeAsRead() {
        MemberPoke poke = createPoke();
        when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.of(poke));

        service.markRead(RECIPIENT_ID, POKE_ID);

        assertThat(poke.isRead()).isTrue();
    }

    @Test
    @DisplayName("수신자가 아닌 회원은 콕찌르기를 읽음 처리할 수 없다")
    void nonRecipientCannotMarkPokeAsRead() {
        MemberPoke poke = createPoke();
        when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.of(poke));

        assertThatThrownBy(() -> service.markRead(SENDER_ID, POKE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.POKE_ACCESS_DENIED));

        assertThat(poke.isRead()).isFalse();
    }

    private MemberPoke createPoke() {
        return MemberPoke.create(
                MemberFixture.createMember(SENDER_ID),
                MemberFixture.createMember(RECIPIENT_ID)
        );
    }
}
