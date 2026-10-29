package com.anabada.fleaflea.domain.notification.controller;

import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.member.service.CustomMemberDetailsService;
import com.anabada.fleaflea.domain.notification.domain.PushPlatform;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceNotOwnerException;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceLimitExceededException;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceRegistrationRateLimitExceededException;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceRegistrationUnavailableException;
import com.anabada.fleaflea.domain.notification.service.PushDeviceService;
import com.anabada.fleaflea.global.config.SecurityConfig;
import com.anabada.fleaflea.global.security.*;
import com.anabada.fleaflea.global.security.oauth2.CustomOidcUserService;
import com.anabada.fleaflea.global.security.oauth2.OAuth2SuccessHandler;
import com.anabada.fleaflea.global.security.oauth2.OAuth2FailureHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PushDeviceController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class PushDeviceControllerTest {

    private static final String FID = "c123456789012345678901";
    private static final String PATH = "/api/v1/notifications/devices";
    private static final String BODY = "{\"fid\":\"" + FID + "\",\"platform\":\"WEB\"}";

    @MockitoBean
    private CustomOidcUserService customOidcUserService;

    @MockitoBean
    private OAuth2SuccessHandler oauth2SuccessHandler;

    @MockitoBean
    private OAuth2FailureHandler oauth2FailureHandler;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PushDeviceService service;

    @MockitoBean
    private CustomMemberDetailsService memberDetails;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private MemberRepository members;

    @Test
    @DisplayName("요청 제한 저장소에 장애가 있으면 503과 도메인 오류 코드를 반환한다")
    void register_rejectsUnavailableLimiter() throws Exception {
        when(service.registerPushDevice(1L, FID, PushPlatform.WEB))
                .thenThrow(new PushDeviceRegistrationUnavailableException());

        mvc.perform(post(PATH)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PUSH_DEVICE_REGISTRATION_UNAVAILABLE"));
    }

    @Test
    @DisplayName("회원의 기기 수가 한도를 넘으면 409와 도메인 오류 코드를 반환한다")
    void register_rejectsDeviceLimit() throws Exception {
        when(service.registerPushDevice(1L, FID, PushPlatform.WEB))
                .thenThrow(new PushDeviceLimitExceededException());

        mvc.perform(post(PATH)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PUSH_DEVICE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("회원의 등록 요청이 너무 많으면 429와 도메인 오류 코드를 반환한다")
    void register_rejectsRequestLimit() throws Exception {
        when(service.registerPushDevice(1L, FID, PushPlatform.WEB))
                .thenThrow(new PushDeviceRegistrationRateLimitExceededException());

        mvc.perform(post(PATH)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PUSH_DEVICE_REGISTRATION_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("기기 등록과 삭제에는 인증이 필요하다")
    void devices_requireAuthentication() throws Exception {
        mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(PATH + "/7")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("인증된 회원의 기기를 등록하고 서버 기기 ID를 반환한다")
    void register_usesAuthenticatedMember() throws Exception {
        when(service.registerPushDevice(1L, FID, PushPlatform.WEB)).thenReturn(7L);

        mvc.perform(post(PATH)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(7));

        verify(service).registerPushDevice(1L, FID, PushPlatform.WEB);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"platform\":null}", "{\"platform\":\"INVALID\"}"})
    @DisplayName("필수 플랫폼 누락과 지원하지 않는 값은 400으로 거절한다")
    void register_rejectsInvalidPlatform(String body) throws Exception {
        body = body.equals("{}") ? "{\"fid\":\"" + FID + "\"}"
                : body.replace("{", "{\"fid\":\"" + FID + "\",");
        mvc.perform(post(PATH)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"platform\":\"WEB\"}", "{\"fid\":null,\"platform\":\"WEB\"}",
            "{\"fid\":\"\",\"platform\":\"WEB\"}", "{\"fid\":\"invalid\",\"platform\":\"WEB\"}"})
    @DisplayName("FID 누락과 잘못된 형식은 400으로 거절한다")
    void register_rejectsInvalidFid(String body) throws Exception {
        mvc.perform(post(PATH)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("기기 ID로 삭제하며 없는 등록도 204를 반환한다")
    void delete_usesDeviceId() throws Exception {
        mvc.perform(delete(PATH + "/7")
                        .with(authentication(principal())))
                .andExpect(status().isNoContent());

        verify(service).deletePushDevice(1L, 7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid"})
    @DisplayName("잘못된 기기 ID는 400으로 거절한다")
    void delete_rejectsInvalidId(String deviceId) throws Exception {
        mvc.perform(delete(PATH + "/" + deviceId)
                        .with(authentication(principal())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("타인의 기기 삭제는 403과 도메인 오류를 반환한다")
    void delete_rejectsOtherOwner() throws Exception {
        doThrow(new PushDeviceNotOwnerException()).when(service).deletePushDevice(1L, 7L);

        mvc.perform(delete(PATH + "/7")
                        .with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PUSH_DEVICE_NOT_OWNER"));
    }

    private UsernamePasswordAuthenticationToken principal() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of());
    }
}
