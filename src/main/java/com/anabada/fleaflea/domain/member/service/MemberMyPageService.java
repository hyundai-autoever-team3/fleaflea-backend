package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.dto.MyProfileResponse;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberMyPageService {
    private final MemberRepository memberRepository;

    public MyProfileResponse getMyProfile(Long memberId) {
        Member me = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        return MyProfileResponse.from(me);

    }



}
