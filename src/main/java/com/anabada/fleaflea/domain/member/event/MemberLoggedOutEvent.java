package com.anabada.fleaflea.domain.member.event;

public record MemberLoggedOutEvent(Long memberId) {

    public static MemberLoggedOutEvent of(Long memberId) {
        return new MemberLoggedOutEvent(memberId);
    }
}
