package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.member.domain.EmailVerificationPurpose;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.dto.LoginRequest;
import com.anabada.fleaflea.domain.member.dto.SignUpRequest;
import com.anabada.fleaflea.domain.member.dto.TokenPair;
import com.anabada.fleaflea.domain.member.exception.InvalidLoginException;
import com.anabada.fleaflea.domain.member.exception.InvalidEmailVerificationTokenException;
import com.anabada.fleaflea.domain.member.exception.MemberEmailDuplicateException;
import com.anabada.fleaflea.domain.member.exception.MemberNicknameDuplicateException;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.member.repository.RedisEmailVerificationRepository;
import com.anabada.fleaflea.domain.notification.repository.PushDeviceRepository;
import com.anabada.fleaflea.domain.refreshtoken.exception.InvalidTokenException;
import com.anabada.fleaflea.domain.refreshtoken.repository.RedisRefreshTokenRepository;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class MemberAuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisRefreshTokenRepository refreshTokenRepository;
    private final TokenIssueService tokenIssueService;
    private final RedisEmailVerificationRepository emailVerificationRepository;
    private final PushDeviceRepository pushDeviceRepository;


    @Transactional
    public void signUp(SignUpRequest request) {
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new MemberNicknameDuplicateException();
        }
        if (memberRepository.existsByEmail(request.email())) {
            throw new MemberEmailDuplicateException();
        }

        boolean verified = emailVerificationRepository.consumeVerificationToken(
                request.verificationToken(),
                request.email(),
                EmailVerificationPurpose.SIGN_UP
        );

        if (!verified) {
            throw new InvalidEmailVerificationTokenException();
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        Member newMember = Member.create(
                request.email(),
                encodedPassword,
                request.nickname()
        );
        memberRepository.save(newMember);
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );
        } catch (BadCredentialsException | InternalAuthenticationServiceException e) {
            throw new InvalidLoginException();
        }
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(MemberNotFoundException::new);

        return tokenIssueService.issueTokenPair(member.getMemberId());
    }

    public TokenPair reissue(String refreshToken) {
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new InvalidTokenException();
        }
        Long memberId;
        try {
            memberId = Long.valueOf(
                    jwtTokenProvider.getMemberId(refreshToken)
            );
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException();
        }

        if (!memberRepository.existsById(memberId)) {
            refreshTokenRepository.deleteByMemberId(memberId);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(memberId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(memberId);

        boolean rotated = refreshTokenRepository.rotate(
                memberId,
                refreshToken,
                newRefreshToken,
                jwtTokenProvider.getRemainingValidity(newRefreshToken)
        );

        if (!rotated) {
            throw new InvalidTokenException();
        }

        return new TokenPair(newAccessToken, newRefreshToken);

    }

    @Transactional
    public void logout(Long memberId) {
        memberRepository.findLockedById(memberId).orElseThrow(MemberNotFoundException::new);
        pushDeviceRepository.deleteAllByMemberId(memberId);
        refreshTokenRepository.deleteByMemberId(memberId);
    }

}
