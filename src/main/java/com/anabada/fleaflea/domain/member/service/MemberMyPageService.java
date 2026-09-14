package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.dto.MyProfileResponse;
import com.anabada.fleaflea.domain.member.dto.PasswordUpdateRequest;
import com.anabada.fleaflea.domain.member.dto.ProfileUpdateRequest;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.exception.PasswordMismatchException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberMyPageService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        return MyProfileResponse.from(member);
    }

    @Transactional
    public void updateMyProfile(Long memberId, ProfileUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        member.updateProfile(
                request.nickname(),
                request.profileImageUrl()
        );

    }

    @Transactional
    public void updatePassword(Long memberId, PasswordUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);
        if (!passwordEncoder.matches(
                request.currentPassword(),
                member.getPassword()
        )) {
            throw new PasswordMismatchException();
        }
        member.updatePassword(
                passwordEncoder.encode(request.newPassword())
        );

    }


}
