package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.member.dto.TokenPair;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.refreshtoken.domain.RefreshToken;
import com.anabada.fleaflea.domain.refreshtoken.repository.RedisRefreshTokenRepository;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenIssueService {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisRefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenPair issueTokenPair(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new MemberNotFoundException();
        }

        String accessToken = jwtTokenProvider.createAccessToken(memberId);
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId);

        refreshTokenRepository.save(
                memberId,
                refreshToken,
                jwtTokenProvider.getRemainingValidity(refreshToken)
        );


        return new TokenPair(accessToken, refreshToken);
    }
}
