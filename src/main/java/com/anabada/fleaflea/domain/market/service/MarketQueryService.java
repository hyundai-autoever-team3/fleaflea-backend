package com.anabada.fleaflea.domain.market.service;

import com.anabada.fleaflea.domain.market.dto.MarketSummaryResponse;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketQueryService {

    private final MarketMemberRepository marketMemberRepository;
    private final MemberRepository memberRepository;

    public List<MarketSummaryResponse> getMarkets(
            Long memberId,
            String scope
    ) {
        if (!"joined".equals(scope)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        return marketMemberRepository
                .findAllByMemberOrderByJoinedAtDesc(member)
                .stream()
                .map(MarketSummaryResponse::from)
                .toList();
    }
}