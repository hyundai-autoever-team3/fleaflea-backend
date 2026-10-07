package com.anabada.fleaflea.domain.chat.event;

public record ChatEvent(
        Long firstMemberId,
        Long secondMemberId,
        String eventName,
        Object payload
) {

    public static final String MESSAGE_SENT = "chat-message";
    public static final String MESSAGES_READ = "chat-read";
    public static final String TYPING_CHANGED = "chat-typing";
}
