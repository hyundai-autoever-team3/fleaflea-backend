package com.anabada.fleaflea.domain.notification.controller;

import com.anabada.fleaflea.domain.member.service.CustomMemberDetailsService;
import com.anabada.fleaflea.domain.notification.service.NotificationService;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.global.config.SecurityConfig;
import com.anabada.fleaflea.global.security.CustomAccessDeniedHandler;
import com.anabada.fleaflea.global.security.CustomAuthenticationEntryPoint;
import com.anabada.fleaflea.global.security.JwtAuthenticationFilter;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
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

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
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
    @DisplayName("인증된 회원의 구독에서 컨트롤러는 JWT를 다시 파싱하지 않는다")
    void subscribe_reusesAuthenticatedMember() throws Exception {
        SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
        when(sse.subscribe(1L)).thenReturn(emitter);

        mvc.perform(get("/api/v1/notifications/subscribe")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(sse).subscribe(1L);
        verifyNoInteractions(tokens);
        emitter.complete();
    }

    @Test
    @DisplayName("연결 시작 시 필터에서 액세스 토큰을 인증한다")
    void subscribe_authenticatesAtConnectionStart() throws Exception {
        SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
        when(tokens.validateToken("access-token")).thenReturn(true);
        when(tokens.isAccessToken("access-token")).thenReturn(true);
        when(tokens.getMemberId("access-token")).thenReturn("1");
        when(sse.subscribe(1L)).thenReturn(emitter);

        mvc.perform(get("/api/v1/notifications/subscribe")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(sse).subscribe(1L);
        verify(tokens, never()).getExpiration(anyString());
        emitter.complete();
    }

    @Test
    @DisplayName("재연결 시 만료되거나 유효하지 않은 토큰으로는 구독할 수 없다")
    void subscribe_rejectsInvalidTokenAtConnectionStart() throws Exception {
        when(tokens.validateToken("expired-token")).thenReturn(false);

        mvc.perform(get("/api/v1/notifications/subscribe")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(sse);
    }

    @Test
    @DisplayName("인증이 없으면 SSE 연결을 생성하지 않는다")
    void subscribe_requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/notifications/subscribe"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(sse);
    }
}
