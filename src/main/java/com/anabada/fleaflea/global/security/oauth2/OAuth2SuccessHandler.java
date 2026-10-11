package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.domain.member.dto.TokenPair;
import com.anabada.fleaflea.domain.member.service.TokenIssueService;
import com.anabada.fleaflea.global.security.RefreshTokenCookieProvider;
import com.anabada.fleaflea.global.security.oauth2.dto.CustomOidcUser;
import com.anabada.fleaflea.global.security.oauth2.exception.UnsupportedOAuth2PrincipalException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import org.springframework.http.HttpHeaders;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final TokenIssueService tokenIssueService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;
    private final PendingOAuth2SignupStore pendingOAuth2SignupStore;
    private final OAuth2SignupCookieProvider oAuth2SignupCookieProvider;
    private final String frontendUrl;

    public OAuth2SuccessHandler(
            TokenIssueService tokenIssueService,
            RefreshTokenCookieProvider refreshTokenCookieProvider,
            PendingOAuth2SignupStore pendingOAuth2SignupStore,
            OAuth2SignupCookieProvider oAuth2SignupCookieProvider,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.tokenIssueService = tokenIssueService;
        this.refreshTokenCookieProvider =
                refreshTokenCookieProvider;
        this.pendingOAuth2SignupStore =
                pendingOAuth2SignupStore;
        this.oAuth2SignupCookieProvider =
                oAuth2SignupCookieProvider;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        if (!(authentication.getPrincipal() instanceof  CustomOidcUser principal)) {
            throw new UnsupportedOAuth2PrincipalException();
        }

        if (principal.isRegistered()) {
            loginRegisteredMember(request, response, principal);
            return;
        }
        redirectToSignup(request, response, principal);
    }


    private void loginRegisteredMember(
            HttpServletRequest request,
            HttpServletResponse response,
            CustomOidcUser principal
    ) throws IOException {
        TokenPair tokenPair = tokenIssueService.issueTokenPair(
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
            CustomOidcUser principal
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
