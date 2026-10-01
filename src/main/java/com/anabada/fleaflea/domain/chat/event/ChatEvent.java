package com.anabada.fleaflea.domain.chat.event;

public record ChatEvent(Long firstMemberId, Long secondMemberId, String name, Object payload) {}
