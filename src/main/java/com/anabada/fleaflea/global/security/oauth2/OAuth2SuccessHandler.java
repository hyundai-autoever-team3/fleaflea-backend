package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.domain.member.dto.TokenPair;
import com.anabada.fleaflea.domain.member.service.MemberAuthService;
import com.anabada.fleaflea.global.security.RefreshTokenCookieProvider;
import com.anabada.fleaflea.global.security.oauth2.dto.CustomOAuth2User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import org.springframework.http.HttpHeaders;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final MemberAuthService memberAuthService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;
    private final PendingOAuth2SignupStore pendingOAuth2SignupStore;
    private final OAuth2SignupCookieProvider oAuth2SignupCookieProvider;
    private final String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();

        if (principal.isRegistered()) {

        }
    }


    private void loginRegisteredMember(
            HttpServletRequest request,
            HttpServletResponse response,
            CustomOAuth2User principal
    ) throws IOException {
        TokenPair tokenPair = memberAuthService.issueTokenPair(
                principal.memberId()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieProvider.create(tokenPair.refreshToken())
                        .toString()
        );
        getRedirectStrategy().sendRedirect(
                request,
                response,
                frontendUrl + "/oauth/success"
        );
    }

    private void redirectToSignup(
            HttpServletRequest request,
            HttpServletResponse response,
            CustomOAuth2User principal
    ) throws IOException {
        String ticket = pendingOAuth2SignupStore.save(
                principal.memberInfo()
        );

        oAuth2SignupCookieProvider.addCookie(
                response,
                ticket
        );

        getRedirectStrategy().sendRedirect(
                request,
                response,
                frontendUrl + "/oauth/signup"
        );
    }
}
