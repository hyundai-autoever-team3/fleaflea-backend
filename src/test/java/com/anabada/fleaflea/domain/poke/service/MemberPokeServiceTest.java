package com.anabada.fleaflea.domain.poke.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.poke.domain.MemberPoke;
import com.anabada.fleaflea.domain.poke.repository.MemberPokeRepository;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberPokeServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock MemberPokeRepository pokeRepository;
    @InjectMocks MemberPokeService pokeService;

    @Test
    void sendSavesSenderAndRecipient() {
        Member sender = MemberFixture.createMember(1L);
        Member recipient = MemberFixture.createMember(2L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(recipient));

        pokeService.send(1L, 2L);

        ArgumentCaptor<MemberPoke> captor = ArgumentCaptor.forClass(MemberPoke.class);
        verify(pokeRepository).save(captor.capture());
        assertThat(captor.getValue().getSender()).isSameAs(sender);
        assertThat(captor.getValue().getRecipient()).isSameAs(recipient);
        assertThat(captor.getValue().isRead()).isFalse();
    }

    @Test
    void cannotPokeSelf() {
        assertThatThrownBy(() -> pokeService.send(1L, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.POKE_SELF));
        verify(pokeRepository, never()).save(any());
    }

    @Test
    void onlyRecipientCanRead() {
        MemberPoke poke = MemberPoke.create(MemberFixture.createMember(1L), MemberFixture.createMember(2L));
        when(pokeRepository.findById(3L)).thenReturn(Optional.of(poke));

        assertThatThrownBy(() -> pokeService.markRead(1L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.POKE_ACCESS_DENIED));
        assertThat(poke.isRead()).isFalse();

        pokeService.markRead(2L, 3L);
        assertThat(poke.isRead()).isTrue();
    }
}
