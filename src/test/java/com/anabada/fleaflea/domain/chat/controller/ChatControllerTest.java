package com.anabada.fleaflea.domain.chat.controller;

import com.anabada.fleaflea.domain.chat.dto.ChatRoomListResponse;
import com.anabada.fleaflea.global.dto.CursorPageResponse;
import com.anabada.fleaflea.domain.chat.exception.ChatNotParticipantException;
import com.anabada.fleaflea.domain.chat.exception.ChatRoomNotFoundException;
import com.anabada.fleaflea.domain.chat.service.ChatService;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
class ChatControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ROOM_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

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

    @Test
    @DisplayName("인증 없이 채팅 목록을 조회하면 401을 반환한다")
    void getChatRooms_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/chat/rooms"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRoomRequests")
    @DisplayName("잘못된 페이지와 메시지 커서는 서비스 호출 전에 거절한다")
    void getRequests_rejectInvalidParameters(
            String caseName,
            String path
    ) throws Exception  {
        mockMvc.perform(get(path).with(authentication(createMemberAuthentication())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("참여자 권한 오류는 403과 채팅 전용 에러 코드를 반환한다")
    void getChatRoom_returnsParticipantError() throws Exception {
        when(chatService.getChatRoom(MEMBER_ID, ROOM_ID)).thenThrow(new ChatNotParticipantException());

        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}", ROOM_ID)
                        .with(authentication(createMemberAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_PARTICIPANT"));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(longs = {0L, -1L})
    @DisplayName("숫자 방 ID는 서비스에 전달하고 조회 결과의 도메인 오류를 반환한다")
    void getChatRoom_delegatesNumericRoomIdToService(Long roomId) throws Exception {
        when(chatService.getChatRoom(MEMBER_ID, roomId)).thenThrow(new ChatRoomNotFoundException());

        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}", roomId)
                        .with(authentication(createMemberAuthentication())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));

        verify(chatService).getChatRoom(MEMBER_ID, roomId);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequiredIdBodies")
    @DisplayName("필수 친구 ID가 없거나 잘못되면 채팅방 생성 요청을 거절한다")
    void getOrCreateChatRoom_rejectsInvalidFriendId(
            String caseName,
            String value
    ) throws Exception  {
        mockMvc.perform(post("/api/v1/chat/rooms")
                        .with(authentication(createMemberAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requiredIdBody("friendId", value)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(chatService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emptyOptionalCursorRequests")
    @DisplayName("선택 커서를 생략하거나 빈 값으로 보내면 null로 전달하고 기본 범위로 조회한다")
    void getMessages_acceptsMissingOrEmptyCursors(
            String caseName,
            String query
    ) throws Exception  {
        when(chatService.getMessages(MEMBER_ID, ROOM_ID, null, null, 30))
                .thenReturn(CursorPageResponse.from(List.of(), null, false));

        mockMvc.perform(get("/api/v1/chat/rooms/10/messages" + query)
                        .with(authentication(createMemberAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(chatService).getMessages(MEMBER_ID, ROOM_ID, null, null, 30);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("defaultPageRequests")
    @DisplayName("페이지와 크기를 생략하거나 빈 값으로 보내면 기본값으로 조회한다")
    void getChatRooms_usesDefaultPagination(
            String caseName,
            String query
    ) throws Exception  {
        when(chatService.getChatRooms(MEMBER_ID, 0, 20))
                .thenReturn(ChatRoomListResponse.from(List.of(), false));

        mockMvc.perform(get("/api/v1/chat/rooms" + query)
                        .with(authentication(createMemberAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms").isEmpty());

        verify(chatService).getChatRooms(MEMBER_ID, 0, 20);
    }

    private static Stream<Arguments> emptyOptionalCursorRequests() {
        return Stream.of(
                Arguments.of("두 커서 누락", ""),
                Arguments.of("beforeId 빈 값", "?beforeId="),
                Arguments.of("afterId 빈 값", "?afterId="),
                Arguments.of("두 커서 빈 값", "?beforeId=&afterId="),
                Arguments.of("크기 빈 값", "?size=")
        );
    }

    private static Stream<Arguments> defaultPageRequests() {
        return Stream.of(
                Arguments.of("페이지와 크기 누락", ""),
                Arguments.of("페이지 빈 값", "?page="),
                Arguments.of("크기 빈 값", "?size="),
                Arguments.of("페이지와 크기 빈 값", "?page=&size=")
        );
    }

    private static Stream<Arguments> invalidRequiredIdBodies() {
        return Stream.of(
                Arguments.of("ID 누락", (String) null),
                Arguments.of("ID null", "null"),
                Arguments.of("숫자가 아닌 ID", "\"invalid\"")
        );
    }

    private static String requiredIdBody(
            String fieldName,
            String value
    ) {
        return value == null ? "{}" : "{\"%s\":%s}".formatted(fieldName, value);
    }

    private UsernamePasswordAuthenticationToken createMemberAuthentication() {
        return new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of());
    }

    private static Stream<Arguments> invalidRoomRequests() {
        return Stream.of(
                Arguments.of("페이지 음수", "/api/v1/chat/rooms?page=-1"),
                Arguments.of("크기 0", "/api/v1/chat/rooms?size=0"),
                Arguments.of("크기 최대값 초과", "/api/v1/chat/rooms?size=101"),
                Arguments.of("페이지 문자열 null", "/api/v1/chat/rooms?page=null"),
                Arguments.of("크기 문자열 null", "/api/v1/chat/rooms?size=null"),
                Arguments.of("방 ID 문자열 null", "/api/v1/chat/rooms/null"),
                Arguments.of("복구 커서 음수", "/api/v1/chat/rooms/10/messages?afterId=-1"),
                Arguments.of("이전 커서 문자열 null", "/api/v1/chat/rooms/10/messages?beforeId=null"),
                Arguments.of("복구 커서 문자열 null", "/api/v1/chat/rooms/10/messages?afterId=null"),
                Arguments.of("메시지 크기 문자열 null", "/api/v1/chat/rooms/10/messages?size=null"),
                Arguments.of("Long 범위 초과", "/api/v1/chat/rooms/10/messages?beforeId=9223372036854775808")
        );
    }

}
