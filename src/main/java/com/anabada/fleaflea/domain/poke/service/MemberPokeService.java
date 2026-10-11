package com.anabada.fleaflea.domain.poke.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.notifier.PokeNotifier;
import com.anabada.fleaflea.domain.poke.domain.MemberPoke;
import com.anabada.fleaflea.domain.poke.dto.MemberPokeResponse;
import com.anabada.fleaflea.domain.poke.event.MemberPokedEvent;
import com.anabada.fleaflea.domain.poke.exception.PokeAccessDeniedException;
import com.anabada.fleaflea.domain.poke.exception.PokeNotFoundException;
import com.anabada.fleaflea.domain.poke.exception.PokeSelfRequestException;
import com.anabada.fleaflea.domain.poke.ratelimit.PokeRateLimiter;
import com.anabada.fleaflea.domain.poke.repository.MemberPokeRepository;
import com.anabada.fleaflea.global.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberPokeService {

    private final MemberRepository memberRepository;
    private final MemberPokeRepository memberPokeRepository;
    private final PokeNotifier pokeNotifier;
    private final PokeRateLimiter pokeRateLimiter;

    @Transactional
    public void sendPoke(
            Long senderId,
            Long recipientId
    ) {
        if (senderId.equals(recipientId)) {
            throw new PokeSelfRequestException();
        }

        Member sender = memberRepository.findById(senderId)
                .orElseThrow(() -> new MemberNotFoundException());
        Member recipient = memberRepository.findById(recipientId)
                .orElseThrow(() -> new MemberNotFoundException());

        pokeRateLimiter.acquire(senderId, recipientId);

        MemberPoke poke = MemberPoke.create(sender, recipient);

        memberPokeRepository.save(poke);

        pokeNotifier.notifyOf(
                MemberPokedEvent.of(
                        poke.getPokeId(),
                        sender.getMemberId(),
                        recipient.getMemberId(),
                        sender.getNickname()
                )
        );
    }

    public PageResponse<MemberPokeResponse> getReceivedPokes(
            Long recipientId,
            int page,
            int size
    ) {
        return PageResponse.from(memberPokeRepository.findByRecipient_MemberId(
                recipientId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(MemberPokeResponse::from));
    }

    @Transactional
    public void markPokeAsRead(
            Long recipientId,
            Long pokeId
    ) {
        MemberPoke poke = memberPokeRepository.findById(pokeId)
                .orElseThrow(() -> new PokeNotFoundException());
        if (!poke.getRecipient().getMemberId().equals(recipientId)) {
            throw new PokeAccessDeniedException();
        }
        poke.markRead();
    }
}
