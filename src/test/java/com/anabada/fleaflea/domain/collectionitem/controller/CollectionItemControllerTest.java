package com.anabada.fleaflea.domain.collectionitem.controller;

import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemUpdateRequest;
import com.anabada.fleaflea.domain.collectionitem.service.CollectionItemService;
import com.anabada.fleaflea.domain.member.service.CustomMemberDetailsService;
import com.anabada.fleaflea.global.config.SecurityConfig;
import com.anabada.fleaflea.global.security.CustomAccessDeniedHandler;
import com.anabada.fleaflea.global.security.CustomAuthenticationEntryPoint;
import com.anabada.fleaflea.global.security.JwtAuthenticationFilter;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CollectionItemController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class CollectionItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CollectionItemService collectionItemService;

    @MockitoBean
    private CustomMemberDetailsService customMemberDetailsService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCreateRequests")
    @DisplayName("도감 등록의 필수값과 길이 검증에 실패하면 서비스를 호출하지 않는다")
    void createCollectionItem_rejectsInvalidInput(
            String caseName,
            String title,
            String description,
            String isPublic,
            byte[] image
    ) throws Exception  {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/collection-items");
        if (title != null) {
            request.param("title", title);
        }
        if (description != null) {
            request.param("description", description);
        }
        if (isPublic != null) {
            request.param("isPublic", isPublic);
        }
        if (image != null) {
            request.file(new MockMultipartFile("image", "test.png", "image/png", image));
        }

        mockMvc.perform(request.with(authentication(createAuthentication())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(collectionItemService);
    }

    @ParameterizedTest(name = "수정 제목: [{0}]")
    @MethodSource("invalidUpdateTitles")
    @DisplayName("제목을 수정할 때 빈 값·공백·최대 길이 초과는 거절한다")
    void updateCollectionItem_rejectsBlankOrTooLongTitle(String title) throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/collection-items/10")
                        .param("title", title)
                        .with(authentication(createAuthentication())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(collectionItemService);
    }

    @Test
    @DisplayName("제목을 생략하고 공개 여부만 수정하면 제목은 null로 전달한다")
    void updateCollectionItem_allowsMissingTitle() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/collection-items/10")
                        .param("isPublic", "false")
                        .with(authentication(createAuthentication())))
                .andExpect(status().isOk());

        verify(collectionItemService).updateCollectionItem(eq(1L), eq(10L),
                eq(new CollectionItemUpdateRequest(null, null, false, null)));
    }

    @Test
    @DisplayName("제목 150자와 설명 1000자는 등록 요청에서 허용한다")
    void createCollectionItem_acceptsMaximumLengths() throws Exception {
        mockMvc.perform(multipart("/api/v1/collection-items")
                        .file(new MockMultipartFile("image", new byte[]{1}))
                        .param("title", "a".repeat(150))
                        .param("description", "a".repeat(1000))
                        .param("isPublic", "false")
                        .with(authentication(createAuthentication())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("인증 없이 도감 목록을 조회하면 거절한다")
    void getMyCollectionItems_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/collection-items"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(collectionItemService);
    }

    private UsernamePasswordAuthenticationToken createAuthentication() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of());
    }

    private static Stream<Arguments> invalidCreateRequests() {
        byte[] image = {1};
        return Stream.of(
                Arguments.of("제목 누락", null, null, "true", image),
                Arguments.of("제목 공백", "   ", null, "true", image),
                Arguments.of("제목 길이 초과", "a".repeat(151), null, "true", image),
                Arguments.of("설명 길이 초과", "제목", "a".repeat(1001), "true", image),
                Arguments.of("공개 여부 누락", "제목", null, null, image),
                Arguments.of("공개 여부 문자열 null", "제목", null, "null", image),
                Arguments.of("이미지 누락", "제목", null, "true", null),
                Arguments.of("빈 이미지", "제목", null, "true", new byte[0])
        );
    }

    private static Stream<String> invalidUpdateTitles() {
        return Stream.of("", "   ", "\t\n", "a".repeat(151));
    }
}
