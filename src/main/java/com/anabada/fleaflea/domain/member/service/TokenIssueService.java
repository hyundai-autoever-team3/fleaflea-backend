package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.member.dto.TokenPair;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.refreshtoken.domain.RefreshToken;
import com.anabada.fleaflea.domain.refreshtoken.repository.RefreshTokenRepository;
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
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenPair issueTokenPair(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new MemberNotFoundException();
        }

        String accessToken = jwtTokenProvider.createAccessToken(memberId);
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        RefreshToken savedRefreshToken = refreshTokenRepository
                .findByMemberId(memberId)
                .orElse(null);

        if (savedRefreshToken != null) {
            savedRefreshToken.update(refreshToken, expiresAt);
        } else {
            refreshTokenRepository.save(
                    RefreshToken.create(
                            memberId,
                            refreshToken,
                            expiresAt
                    )
            );
        }

        return new TokenPair(accessToken, refreshToken);
    }
}
