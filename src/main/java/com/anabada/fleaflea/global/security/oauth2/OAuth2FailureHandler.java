package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2LoginException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import org.springframework.security.core.AuthenticationException;
import java.io.IOException;

@Slf4j
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {
    private final String frontendUrl;

    public OAuth2FailureHandler(
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {

        ErrorCode errorCode =
                resolveErrorCode(exception);

        log.warn(
                "[OAuth2 login failed] code={}, exception={}",
                errorCode.getCode(),
                exception.getClass().getSimpleName()
        );

        String redirectUrl = UriComponentsBuilder
                .fromUriString(frontendUrl)
                .path("/oauth/failure")
                .queryParam(
                        "error",
                        errorCode.getCode()
                )
                .build()
                .encode()
                .toUriString();

        getRedirectStrategy().sendRedirect(
                request,
                response,
                redirectUrl
        );
    }

    private ErrorCode resolveErrorCode(
            AuthenticationException exception
    ) {
        if (exception instanceof OAuth2LoginException oauthException) {
            return oauthException.getErrorCode();
        }

        return ErrorCode.OAUTH2_LOGIN_FAILED;
    }
}
