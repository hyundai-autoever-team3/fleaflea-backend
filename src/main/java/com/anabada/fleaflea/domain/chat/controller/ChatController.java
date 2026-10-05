package com.anabada.fleaflea.domain.chat.controller;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatReadRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomCreateRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomListResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomResponse;
import com.anabada.fleaflea.domain.chat.service.ChatService;
import com.anabada.fleaflea.global.dto.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms")
@Tag(name = "친구 채팅")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(
            summary = "친구와 채팅 시작",
            description = "ACCEPTED 친구만 가능. 같은 두 사람의 기존 채팅방이 있으면 반환합니다."
    )
    public ChatRoomResponse getOrCreateChatRoom(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ChatRoomCreateRequest request
    ) {
        return chatService.getOrCreateChatRoom(memberId, request.friendId());
    }

    @GetMapping
    @Operation(
            summary = "내 채팅 목록",
            description = "최근 활동순. page는 0부터, size는 1~100. 친구 해제 후에도 이전 대화를 조회할 수 있습니다."
    )
    public ChatRoomListResponse getChatRooms(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return chatService.getChatRooms(memberId, page, size);
    }

    @GetMapping("/{roomId}")
    @Operation(
            summary = "채팅방 상세",
            description = "참여자만 조회할 수 있습니다. canSend로 현재 전송 가능 여부를 확인합니다."
    )
    public ChatRoomResponse getChatRoom(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId
    ) {
        return chatService.getChatRoom(memberId, roomId);
    }

    @PostMapping("/{roomId}/messages")
    @Operation(
            summary = "메시지 전송",
            description = "최대 2000자, 회원당 분당 60개. clientMessageId는 UUID이며 재시도 시 같은 값을 사용합니다. 저장 완료 후 SSE chat-message 이벤트를 전달합니다."
    )
    public ChatMessageResponse sendMessage(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @Valid @RequestBody ChatMessageSendRequest request
    ) {
        return chatService.sendMessage(memberId, roomId, request);
    }

    @GetMapping("/{roomId}/messages")
    @Operation(
            summary = "메시지 조회",
            description = "기본 최신순. beforeId는 과거 조회(내림차순), afterId는 누락 복구(오름차순, 0부터 가능). "
                    + "두 커서는 동시에 사용할 수 없습니다. hasNext=true면 nextCursor로 계속 조회합니다."
    )
    public CursorPageResponse<ChatMessageResponse> getMessages(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(0) Long afterId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size
    ) {
        return chatService.getMessages(memberId, roomId, beforeId, afterId, size);
    }

    @PatchMapping("/{roomId}/read")
    @Operation(
            summary = "메시지 읽음 처리",
            description = "실제로 확인한 메시지 ID를 전달합니다. 읽음 위치는 뒤로 이동하지 않습니다. SSE chat-read 이벤트를 전달합니다."
    )
    public ChatReadResponse markMessagesAsRead(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @Valid @RequestBody ChatReadRequest request
    ) {
        return chatService.markMessagesAsRead(memberId, roomId, request.messageId());
    }
}
