package com.anabada.fleaflea.domain.poke.dto;

import com.anabada.fleaflea.domain.poke.domain.MemberPoke;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "받은 콕찌르기")
public record MemberPokeResponse(
        Long pokeId,
        Long senderId,
        String senderNickname,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static MemberPokeResponse from(MemberPoke poke) {
        return new MemberPokeResponse(
                poke.getPokeId(),
                poke.getSender().getMemberId(),
                poke.getSender().getNickname(),
                poke.isRead(),
                poke.getCreatedAt()
        );
    }
}
