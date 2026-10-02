package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.dto.OAuth2MemberInfo;
import com.anabada.fleaflea.domain.member.dto.OAuth2SignupRequest;
import com.anabada.fleaflea.domain.member.dto.TokenPair;
import com.anabada.fleaflea.domain.member.exception.MemberEmailDuplicateException;
import com.anabada.fleaflea.domain.member.exception.MemberNicknameDuplicateException;
import com.anabada.fleaflea.domain.member.exception.OAuth2SignupExpiredException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.security.oauth2.PendingOAuth2SignupStore;
import com.anabada.fleaflea.global.security.oauth2.dto.PendingOAuth2Signup;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuth2SignupService {

    private final PendingOAuth2SignupStore pendingOAuth2SignupStore;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssueService tokenIssueService;

    @Transactional
    public TokenPair signup(
            String ticket,
            OAuth2SignupRequest request
    ) {
        PendingOAuth2Signup pendingOAuth2Signup =
                pendingOAuth2SignupStore.consume(ticket)
                        .orElseThrow(
                                OAuth2SignupExpiredException::new
                        );

        OAuth2MemberInfo memberInfo =
                pendingOAuth2Signup.memberInfo();

        if (memberRepository.existsByOauth2ProviderAndOauth2Id(
                memberInfo.provider(),
                memberInfo.providerId()
        )) {
            throw new MemberEmailDuplicateException();
        }

        if (memberRepository.existsByEmail(memberInfo.email())) {
            throw new MemberEmailDuplicateException();
        }

        if (memberRepository.existsByNickname(request.nickname())) {
            throw new MemberNicknameDuplicateException();
        }

        String password = passwordEncoder.encode(
                UUID.randomUUID().toString()
        );

        Member member = memberRepository.save(
                Member.createOAuth2(
                        memberInfo.provider(),
                        memberInfo.providerId(),
                        memberInfo.email(),
                        password,
                        request.nickname()
                )
        );
        return tokenIssueService.issueTokenPair(
                member.getMemberId()
        );
    }
}
