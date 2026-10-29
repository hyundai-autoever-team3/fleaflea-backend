package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.dto.LogoutRequest;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.member.service.CustomMemberDetailsService;
import com.anabada.fleaflea.domain.member.service.MemberAuthService;
import com.anabada.fleaflea.domain.member.service.MemberService;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceNotOwnerException;
import com.anabada.fleaflea.global.config.SecurityConfig;
import com.anabada.fleaflea.global.security.CustomAccessDeniedHandler;
import com.anabada.fleaflea.global.security.CustomAuthenticationEntryPoint;
import com.anabada.fleaflea.global.security.JwtAuthenticationFilter;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import com.anabada.fleaflea.global.security.RefreshTokenCookieProvider;
import com.anabada.fleaflea.global.security.oauth2.CustomOidcUserService;
import com.anabada.fleaflea.global.security.oauth2.OAuth2FailureHandler;
import com.anabada.fleaflea.global.security.oauth2.OAuth2SuccessHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MemberAuthController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class MemberAuthControllerTest {

    private static final String PATH = "/api/v1/auth/logout";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MemberAuthService service;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private RefreshTokenCookieProvider cookies;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private MemberRepository members;

    @MockitoBean
    private CustomMemberDetailsService memberDetails;

    @MockitoBean
    private CustomOidcUserService customOidcUserService;

    @MockitoBean
    private OAuth2SuccessHandler oauth2SuccessHandler;

    @MockitoBean
    private OAuth2FailureHandler oauth2FailureHandler;

    @Test
    @DisplayName("로그아웃은 인증 회원과 현재 기기 ID를 전달하고 Refresh Token 쿠키를 만료시킨다")
    void logout_passesCurrentDeviceAndExpiresCookie() throws Exception {
        when(cookies.delete()).thenReturn(ResponseCookie.from("refresh_token", "").maxAge(0).build());

        mvc.perform(delete(PATH)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":7}"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        verify(service).logout(1L, LogoutRequest.of(7L));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"deviceId\":null}"})
    @DisplayName("본문이나 기기 ID가 없는 기존 로그아웃 요청도 허용한다")
    void logout_allowsMissingDeviceId(String body) throws Exception {
        when(cookies.delete()).thenReturn(ResponseCookie.from("refresh_token", "").maxAge(0).build());
        var request = delete(PATH).with(authentication(principal()));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        mvc.perform(request)
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        verify(service).logout(1L, body == null ? null : LogoutRequest.of(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "\"invalid\""})
    @DisplayName("양수가 아니거나 형식이 잘못된 기기 ID는 로그아웃 전에 거절한다")
    void logout_rejectsInvalidDeviceId(String deviceId) throws Exception {
        mvc.perform(delete(PATH)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":" + deviceId + "}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service, cookies);
    }

    @Test
    @DisplayName("다른 회원의 기기로 로그아웃하면 403을 반환하며 쿠키를 삭제하지 않는다")
    void logout_rejectsOtherMembersDevice() throws Exception {
        doThrow(new PushDeviceNotOwnerException()).when(service).logout(1L, LogoutRequest.of(7L));

        mvc.perform(delete(PATH)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":7}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PUSH_DEVICE_NOT_OWNER"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(cookies);
    }

    @Test
    @DisplayName("로그아웃 요청에는 인증이 필요하다")
    void logout_requiresAuthentication() throws Exception {
        mvc.perform(delete(PATH)).andExpect(status().isUnauthorized());

        verifyNoInteractions(service, cookies);
    }

    private UsernamePasswordAuthenticationToken principal() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of());
    }
}
