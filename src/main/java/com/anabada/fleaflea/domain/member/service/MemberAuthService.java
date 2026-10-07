package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.dto.LoginRequest;
import com.anabada.fleaflea.domain.member.dto.SignUpRequest;
import com.anabada.fleaflea.domain.member.dto.TokenPair;
import com.anabada.fleaflea.domain.member.exception.InvalidLoginException;
import com.anabada.fleaflea.domain.member.exception.MemberEmailDuplicateException;
import com.anabada.fleaflea.domain.member.exception.MemberNicknameDuplicateException;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.refreshtoken.dto.ReissueResponse;
import com.anabada.fleaflea.domain.refreshtoken.exception.InvalidTokenException;
import com.anabada.fleaflea.domain.refreshtoken.service.RefreshTokenService;
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
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public void signUp(SignUpRequest signUpRequest) {
        if (memberRepository.existsByNickname(signUpRequest.nickname())) {
            throw new MemberNicknameDuplicateException();
        }
        if (memberRepository.existsByEmail(signUpRequest.email())) {
            throw new MemberEmailDuplicateException();
        }

        String encodedPassword = passwordEncoder.encode(signUpRequest.password());
        Member newMember = Member.create(
                signUpRequest.email(),
                encodedPassword,
                signUpRequest.nickname()
        );
        memberRepository.save(newMember);
    }

    @Transactional
    public TokenPair login(LoginRequest loginRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.email(),
                            loginRequest.password()
                    )
            );
        } catch (BadCredentialsException | InternalAuthenticationServiceException e) {
            throw new InvalidLoginException();
        }
        Member member = memberRepository.findByEmail(loginRequest.email())
                .orElseThrow(MemberNotFoundException::new);

        String accessToken = jwtTokenProvider.createAccessToken(member.getMemberId());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getMemberId());

        refreshTokenService.saveRefreshToken(member.getMemberId(), refreshToken);

        return new TokenPair(
                accessToken,
                refreshToken
        );
    }

    @Transactional(readOnly = true)
    public ReissueResponse reissue(String refreshToken) {
        Long memberId = refreshTokenService.getMemberIdFromValidSession(refreshToken);
        if (!memberRepository.existsById(memberId)) {
            throw new InvalidTokenException();
        }

        String accessToken = jwtTokenProvider.createAccessToken(memberId);
        return new ReissueResponse(accessToken);
    }

    public void logout(Long memberId, String refreshToken) {
        refreshTokenService.deleteSession(memberId, refreshToken);
    }

    public void logoutAll(Long memberId) {
        refreshTokenService.deleteAllSessions(memberId);
    }
}
