package com.anabada.fleaflea.domain.poke.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.poke.domain.MemberPoke;
import com.anabada.fleaflea.domain.poke.dto.MemberPokeResponse;
import com.anabada.fleaflea.domain.poke.repository.MemberPokeRepository;
import com.anabada.fleaflea.global.dto.PageResponse;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
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
    private final MemberPokeRepository pokeRepository;

    @Transactional
    public void send(Long senderId, Long recipientId) {
        if (senderId.equals(recipientId)) {
            throw new BusinessException(ErrorCode.POKE_SELF);
        }

        Member sender = memberRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Member recipient = memberRepository.findById(recipientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        pokeRepository.save(MemberPoke.create(sender, recipient));
    }

    public PageResponse<MemberPokeResponse> received(Long recipientId, int page, int size) {
        return PageResponse.from(pokeRepository.findByRecipient_MemberId(
                recipientId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(MemberPokeResponse::from));
    }

    @Transactional
    public void markRead(Long recipientId, Long pokeId) {
        MemberPoke poke = pokeRepository.findById(pokeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POKE_NOT_FOUND));
        if (!poke.getRecipient().getMemberId().equals(recipientId)) {
            throw new BusinessException(ErrorCode.POKE_ACCESS_DENIED);
        }
        poke.markRead();
    }
}
