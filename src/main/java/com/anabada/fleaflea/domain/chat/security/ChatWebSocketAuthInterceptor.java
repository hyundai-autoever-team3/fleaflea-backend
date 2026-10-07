package com.anabada.fleaflea.domain.chat.security;

import com.anabada.fleaflea.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ChatWebSocketAuthInterceptor implements ChannelInterceptor {

    public static final String TOKEN_EXPIRATION_ATTRIBUTE = "chatTokenExpiration";
    private static final Pattern SEND_DESTINATION =
            Pattern.compile("/app/chat/rooms/-?\\d+/(messages|read|typing)");
    private static final List<String> SUBSCRIBE_DESTINATIONS = List.of(
            "/user/queue/chat", "/user/queue/chat-acks", "/user/queue/chat-errors"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final ChatWebSocketSessionDecorator chatWebSocketSessionDecorator;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        try {
            return authorizeMessage(message, accessor);
        } catch (AuthenticationException | AccessDeniedException exception) {
            chatWebSocketSessionDecorator.closeRejectedSession(accessor.getSessionId());
            throw exception;
        }
    }

    private Message<?> authorizeMessage(Message<?> message, StompHeaderAccessor accessor) {
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            authenticate(accessor);
            return message;
        }

        if (command == StompCommand.DISCONNECT) {
            return message;
        }

        validateAuthentication(accessor);
        String destination = accessor.getDestination();
        if (command == StompCommand.SEND) {
            if (destination == null || !SEND_DESTINATION.matcher(destination).matches()) {
                throw new AccessDeniedException("허용되지 않은 채팅 전송 경로입니다.");
            }
        } else if (command == StompCommand.SUBSCRIBE) {
            if (destination == null || !SUBSCRIBE_DESTINATIONS.contains(destination)) {
                throw new AccessDeniedException("본인의 채팅 이벤트만 구독할 수 있습니다.");
            }
        } else if (command != StompCommand.UNSUBSCRIBE) {
            throw new AccessDeniedException("허용되지 않은 STOMP 명령입니다.");
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        accessor.removeNativeHeader("Authorization");
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null || sessionAttributes.containsKey(TOKEN_EXPIRATION_ATTRIBUTE)) {
            throw new BadCredentialsException("새 WebSocket 연결에서 인증해주세요.");
        }

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BadCredentialsException("채팅 연결에 액세스 토큰이 필요합니다.");
        }

        String accessToken = authorization.substring(7);
        validateAccessToken(accessToken);
        Long memberId;
        try {
            memberId = Long.valueOf(jwtTokenProvider.getMemberId(accessToken));
        } catch (RuntimeException exception) {
            throw new BadCredentialsException("유효하지 않은 액세스 토큰입니다.", exception);
        }

        sessionAttributes.put(TOKEN_EXPIRATION_ATTRIBUTE, jwtTokenProvider.getExpiration(accessToken).getTime());
        accessor.setUser(new UsernamePasswordAuthenticationToken(memberId.toString(), null, List.of()));
    }

    private void validateAuthentication(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (accessor.getUser() == null || sessionAttributes == null
                || !(sessionAttributes.get(TOKEN_EXPIRATION_ATTRIBUTE) instanceof Long expiresAt)) {
            throw new BadCredentialsException("인증된 채팅 연결이 필요합니다.");
        }

        if (expiresAt <= System.currentTimeMillis()) {
            throw new BadCredentialsException("액세스 토큰이 만료되었습니다.");
        }
    }

    private void validateAccessToken(String accessToken) {
        if (!jwtTokenProvider.isAccessToken(accessToken)) {
            throw new BadCredentialsException("액세스 토큰이 만료되었거나 유효하지 않습니다.");
        }
    }
}
