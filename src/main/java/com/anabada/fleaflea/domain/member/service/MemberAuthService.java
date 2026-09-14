package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.dto.LoginRequest;
import com.anabada.fleaflea.domain.member.dto.LoginResponse;
import com.anabada.fleaflea.domain.member.dto.SignUpRequest;
import com.anabada.fleaflea.domain.member.exception.InvalidLoginException;
import com.anabada.fleaflea.domain.member.exception.MemberEmailDuplicateException;
import com.anabada.fleaflea.domain.member.exception.MemberNicknameDuplicateException;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.refreshtoken.domain.RefreshToken;
import com.anabada.fleaflea.domain.refreshtoken.dto.ReissueRequest;
import com.anabada.fleaflea.domain.refreshtoken.dto.ReissueResponse;
import com.anabada.fleaflea.domain.refreshtoken.exception.InvalidTokenException;
import com.anabada.fleaflea.domain.refreshtoken.repository.RefreshTokenRepository;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberAuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;


    public void signUp(SignUpRequest request) {
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new MemberNicknameDuplicateException();
        }
        if (memberRepository.existsByEmail(request.email())) {
            throw new MemberEmailDuplicateException();
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        Member newMember = Member.create(
                request.email(),
                encodedPassword,
                request.nickname(),
                request.profileImageUrl()
        );
        memberRepository.save(newMember);
    }

    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );
        } catch (BadCredentialsException e) {
            throw new InvalidLoginException();
        }
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(MemberNotFoundException::new);

        String accessToken = jwtTokenProvider.createAccessToken(member.getMemberId());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getMemberId());

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        refreshTokenRepository.save(
                RefreshToken.create(
                        member.getMemberId(),
                        refreshToken,
                        expiresAt
                )
        );
        return new LoginResponse(
                accessToken,
                refreshToken
        );
    }

    public ReissueResponse reissue(ReissueRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtTokenProvider.validateToken(refreshToken) && !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new InvalidTokenException();
        }
        RefreshToken savedToken = refreshTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(InvalidTokenException::new);
        Long memberId = Long.valueOf(jwtTokenProvider.getMemberId(refreshToken));
        String accessToken = jwtTokenProvider.createAccessToken(memberId);
        return new ReissueResponse(accessToken);

    }





}
