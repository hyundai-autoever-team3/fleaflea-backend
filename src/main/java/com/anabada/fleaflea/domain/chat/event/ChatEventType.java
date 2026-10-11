package com.anabada.fleaflea.domain.chat.event;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatEventType {

    MESSAGE_SENT("chat-message"),
    MESSAGES_READ("chat-read"),
    TYPING_CHANGED("chat-typing");

    @JsonValue
    private final String value;
}
