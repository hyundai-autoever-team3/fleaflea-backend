package com.anabada.fleaflea.domain.notification.controller;

import com.anabada.fleaflea.domain.member.service.CustomMemberDetailsService;
import com.anabada.fleaflea.domain.notification.service.NotificationService;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.global.config.SecurityConfig;
import com.anabada.fleaflea.global.security.CustomAccessDeniedHandler;
import com.anabada.fleaflea.global.security.CustomAuthenticationEntryPoint;
import com.anabada.fleaflea.global.security.JwtAuthenticationFilter;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import io.jsonwebtoken.MalformedJwtException;
import com.anabada.fleaflea.global.security.oauth2.CustomOidcUserService;
import com.anabada.fleaflea.global.security.oauth2.OAuth2SuccessHandler;
import com.anabada.fleaflea.global.security.oauth2.OAuth2FailureHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class NotificationControllerTest {

    @MockitoBean
    private CustomOidcUserService customOidcUserService;

    @MockitoBean
    private OAuth2SuccessHandler oauth2SuccessHandler;

    @MockitoBean
    private OAuth2FailureHandler oauth2FailureHandler;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSseService sse;

    @MockitoBean
    private JwtTokenProvider tokens;

    @MockitoBean
    private CustomMemberDetailsService memberDetails;

    @Test
    @DisplayName("인증 토큰의 만료 시각을 SSE 구독에 전달한다")
    void subscribe_passesAuthenticatedTokenExpiration() throws Exception {
        Instant expiresAt = Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        SseEmitter emitter = new SseEmitter(60_000L);
        when(tokens.getExpiration("access-token")).thenReturn(Date.from(expiresAt));
        when(sse.subscribe(1L, expiresAt)).thenReturn(emitter);

        mvc.perform(get("/api/v1/notifications/subscribe")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of())))
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(sse).subscribe(1L, expiresAt);
        emitter.complete();
    }

    @Test
    @DisplayName("토큰 만료 시각을 확인할 수 없으면 SSE 연결을 생성하지 않는다")
    void subscribe_rejectsInvalidExpirationToken() throws Exception {
        when(tokens.getExpiration("invalid-token")).thenThrow(new MalformedJwtException("invalid"));

        mvc.perform(get("/api/v1/notifications/subscribe")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of())))
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(sse);
    }

    @Test
    @DisplayName("인증이 없으면 SSE 연결을 생성하지 않는다")
    void subscribe_requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/notifications/subscribe")).andExpect(status().isUnauthorized());

        verifyNoInteractions(sse);
    }
}
