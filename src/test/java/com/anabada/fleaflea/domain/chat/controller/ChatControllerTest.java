package com.anabada.fleaflea.domain.chat.controller;

import com.anabada.fleaflea.domain.chat.domain.ChatMessage;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomListResponse;
import com.anabada.fleaflea.global.dto.CursorPageResponse;
import com.anabada.fleaflea.domain.chat.exception.ChatNotParticipantException;
import com.anabada.fleaflea.domain.chat.service.ChatService;
import com.anabada.fleaflea.domain.member.service.CustomMemberDetailsService;
import com.anabada.fleaflea.fixture.ChatFixture;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private static final String CLIENT_MESSAGE_ID = "550e8400-e29b-41d4-a716-446655440000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private CustomMemberDetailsService customMemberDetailsService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMessageBodies")
    @DisplayName("잘못된 메시지 입력은 INVALID_REQUEST를 반환하고 서비스를 호출하지 않는다")
    void sendMessage_rejectsInvalidRequest(String caseName, String body) throws Exception {
        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", ROOM_ID)
                        .with(authentication(createMemberAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("2000자 메시지를 전송하면 기존 메시지 응답 필드로 결과를 반환한다")
    void sendMessage_acceptsMaximumLengthAndPreservesResponseFields() throws Exception {
        ChatMessageSendRequest request = ChatFixture.createSendRequest("a".repeat(2000));
        ChatMessage message = ChatFixture.createChatMessageWithId(
                20L, ROOM_ID, MEMBER_ID, request, LocalDateTime.of(2026, 1, 1, 12, 0)
        );
        when(chatService.sendMessage(eq(MEMBER_ID), eq(ROOM_ID), any(ChatMessageSendRequest.class)))
                .thenReturn(ChatMessageResponse.from(message));

        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", ROOM_ID)
                        .with(authentication(createMemberAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonMapper.builder().build().writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.senderId").value(MEMBER_ID))
                .andExpect(jsonPath("$.content").value(request.content()))
                .andExpect(jsonPath("$.clientMessageId").value(request.clientMessageId().toString()))
                .andExpect(jsonPath("$.createdAt").exists());
    }

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
    void getRequests_rejectInvalidParameters(String caseName, String path) throws Exception {
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequiredIdBodies")
    @DisplayName("필수 친구 ID가 없거나 잘못되면 채팅방 생성 요청을 거절한다")
    void getOrCreateChatRoom_rejectsInvalidFriendId(String caseName, String value) throws Exception {
        mockMvc.perform(post("/api/v1/chat/rooms")
                        .with(authentication(createMemberAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requiredIdBody("friendId", value)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(chatService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequiredIdBodies")
    @DisplayName("필수 메시지 ID가 없거나 잘못되면 읽음 처리 요청을 거절한다")
    void markMessagesAsRead_rejectsInvalidMessageId(String caseName, String value) throws Exception {
        mockMvc.perform(patch("/api/v1/chat/rooms/{roomId}/read", ROOM_ID)
                        .with(authentication(createMemberAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requiredIdBody("messageId", value)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(chatService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emptyOptionalCursorRequests")
    @DisplayName("선택 커서를 생략하거나 빈 값으로 보내면 null로 전달하고 기본 범위로 조회한다")
    void getMessages_acceptsMissingOrEmptyCursors(String caseName, String query) throws Exception {
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
    void getChatRooms_usesDefaultPagination(String caseName, String query) throws Exception {
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
                Arguments.of("ID 0", "0"),
                Arguments.of("ID 음수", "-1"),
                Arguments.of("숫자가 아닌 ID", "\"invalid\"")
        );
    }

    private static String requiredIdBody(String fieldName, String value) {
        return value == null ? "{}" : "{\"%s\":%s}".formatted(fieldName, value);
    }

    private UsernamePasswordAuthenticationToken createMemberAuthentication() {
        return new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of());
    }

    private static Stream<Arguments> invalidMessageBodies() {
        String clientMessageId = "\"" + CLIENT_MESSAGE_ID + "\"";

        return Stream.of(
                Arguments.of("요청 본문 null", "null"),
                Arguments.of("요청 본문 없음", ""),
                Arguments.of("빈 객체", "{}"),
                Arguments.of("null 내용", messageBody("null", clientMessageId)),
                Arguments.of("내용 누락", "{\"clientMessageId\":" + clientMessageId + "}"),
                Arguments.of("빈 내용", messageBody("\"\"", clientMessageId)),
                Arguments.of("공백 내용", messageBody("\"   \"", clientMessageId)),
                Arguments.of("2001자 내용", messageBody("\"" + "a".repeat(2001) + "\"", clientMessageId)),
                Arguments.of("null UUID", messageBody("\"안녕\"", "null")),
                Arguments.of("UUID 누락", "{\"content\":\"안녕\"}"),
                Arguments.of("잘못된 UUID", messageBody("\"안녕\"", "\"not-a-uuid\""))
        );
    }

    private static Stream<Arguments> invalidRoomRequests() {
        return Stream.of(
                Arguments.of("페이지 음수", "/api/v1/chat/rooms?page=-1"),
                Arguments.of("크기 0", "/api/v1/chat/rooms?size=0"),
                Arguments.of("크기 최대값 초과", "/api/v1/chat/rooms?size=101"),
                Arguments.of("페이지 문자열 null", "/api/v1/chat/rooms?page=null"),
                Arguments.of("크기 문자열 null", "/api/v1/chat/rooms?size=null"),
                Arguments.of("방 ID 0", "/api/v1/chat/rooms/0"),
                Arguments.of("방 ID 문자열 null", "/api/v1/chat/rooms/null"),
                Arguments.of("이전 커서 0", "/api/v1/chat/rooms/10/messages?beforeId=0"),
                Arguments.of("복구 커서 음수", "/api/v1/chat/rooms/10/messages?afterId=-1"),
                Arguments.of("이전 커서 문자열 null", "/api/v1/chat/rooms/10/messages?beforeId=null"),
                Arguments.of("복구 커서 문자열 null", "/api/v1/chat/rooms/10/messages?afterId=null"),
                Arguments.of("메시지 크기 문자열 null", "/api/v1/chat/rooms/10/messages?size=null"),
                Arguments.of("Long 범위 초과", "/api/v1/chat/rooms/10/messages?beforeId=9223372036854775808")
        );
    }

    private static String messageBody(String content, String clientMessageId) {
        return "{\"content\":%s,\"clientMessageId\":%s}".formatted(content, clientMessageId);
    }
}
