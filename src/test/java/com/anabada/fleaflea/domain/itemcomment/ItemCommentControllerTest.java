package com.anabada.fleaflea.domain.itemcomment;

import com.anabada.fleaflea.domain.itemcomment.controller.ItemCommentController;
import com.anabada.fleaflea.domain.itemcomment.service.ItemCommentService;
import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentCreateRequest;
import com.anabada.fleaflea.domain.member.service.CustomMemberDetailsService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemCommentController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class ItemCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ItemCommentService commentService;

    @MockitoBean
    private CustomMemberDetailsService customMemberDetailsService;

    @MockitoBean
    private CustomOidcUserService customOidcUserService;

    @MockitoBean
    private OAuth2SuccessHandler oauth2SuccessHandler;

    @MockitoBean
    private OAuth2FailureHandler oauth2FailureHandler;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;


    @ParameterizedTest
    @MethodSource("invalidContents")
    @DisplayName("댓글 작성·수정 시 빈 내용과 2000자 초과 요청을 거절한다")
    void rejectsInvalidContent(String content) throws Exception {
        String body = "{\"content\":\"" + content + "\"}";
        mockMvc.perform(post("/api/v1/items/1/comments")
                        .with(authentication(createAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/items/1/comments/2")
                        .with(authentication(createAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(commentService);
    }

    static Stream<String> invalidContents() {
        return Stream.of("", "   ", "가".repeat(2001));
    }

    @Test
    @DisplayName("로그인 없이 댓글 조회·작성·수정·삭제할 수 없다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/items/1/comments")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/items/1/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"질문\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/items/1/comments/2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"수정\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/items/1/comments/2")).andExpect(status().isUnauthorized());
        verifyNoInteractions(commentService);
    }

    @Test
    @DisplayName("유효한 댓글 작성 요청의 인증 회원과 부모 ID를 서비스로 전달한다")
    void createsReply() throws Exception {
        mockMvc.perform(post("/api/v1/items/1/comments")
                        .with(authentication(createAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"답변\",\"parentId\":2}"))
                .andExpect(status().isCreated());
        verify(commentService).createComment(1L, 43L, new ItemCommentCreateRequest("답변", 2L));
    }

    @Test
    @DisplayName("음수 페이지·범위 밖 크기·잘못된 부모 ID를 거절한다")
    void rejectsInvalidPagination() throws Exception {
        for (String query : List.of("page=-1", "size=0", "size=101", "parentId=0")) {
            mockMvc.perform(get("/api/v1/items/1/comments?" + query)
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/items/1/comments")
                        .with(authentication(createAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"답글\",\"parentId\":-1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(commentService);
    }

    private UsernamePasswordAuthenticationToken createAuthentication() {
        return new UsernamePasswordAuthenticationToken(43L, null, List.of());
    }
}
