package com.anabada.fleaflea.domain.chat.controller;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatReadRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatTypingRequest;
import com.anabada.fleaflea.domain.chat.service.ChatService;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.exception.ErrorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatSocketController {

    private final ChatService chatService;

    @MessageMapping("/chat/rooms/{roomId}/messages")
    @SendToUser(value = "/queue/chat-acks", broadcast = false)
    public ChatMessageResponse sendMessage(
            Principal principal,
            @DestinationVariable Long roomId,
            @Valid @Payload ChatMessageSendRequest request
    ) {
        return chatService.sendMessage(Long.valueOf(principal.getName()), roomId, request);
    }

    @MessageMapping("/chat/rooms/{roomId}/read")
    @SendToUser(value = "/queue/chat-acks", broadcast = false)
    public ChatReadResponse markMessagesAsRead(
            Principal principal,
            @DestinationVariable Long roomId,
            @Valid @Payload ChatReadRequest request
    ) {
        return chatService.markMessagesAsRead(Long.valueOf(principal.getName()), roomId, request.messageId());
    }

    @MessageMapping("/chat/rooms/{roomId}/typing")
    public void updateTypingStatus(
            Principal principal,
            @DestinationVariable Long roomId,
            @Valid @Payload ChatTypingRequest request
    ) {
        chatService.updateTypingStatus(Long.valueOf(principal.getName()), roomId, request.typing());
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(value = "/queue/chat-errors", broadcast = false)
    public ErrorResponse handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ErrorResponse.of(errorCode.getCode(), errorCode.getMessage());
    }

    @MessageExceptionHandler({
            MethodArgumentResolutionException.class,
            MessageConversionException.class,
            ConversionException.class,
            IllegalArgumentException.class
    })
    @SendToUser(value = "/queue/chat-errors", broadcast = false)
    public ErrorResponse handleInvalidRequest(Exception exception) {
        return ErrorResponse.of(ErrorCode.INVALID_REQUEST.getCode(), ErrorCode.INVALID_REQUEST.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(value = "/queue/chat-errors", broadcast = false)
    public ErrorResponse handleUnexpectedException(Exception exception) {
        log.error("채팅 WebSocket 요청 처리 실패", exception);
        return ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }
}
