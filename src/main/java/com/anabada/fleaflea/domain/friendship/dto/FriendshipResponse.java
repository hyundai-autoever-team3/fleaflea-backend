package com.anabada.fleaflea.domain.friendship.dto;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.RelationshipStatus;
import com.anabada.fleaflea.domain.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "친구 관계 응답")
public record FriendshipResponse(

        @Schema(description = "친구 관계 ID", example = "1")
        Long friendshipId,

        @Schema(description = "회원 ID", example = "10")
        Long memberId,

        @Schema(description = "회원 닉네임", example = "test")
        String nickname,

        @Schema(
                description = "프로필 이미지 URL. 프로필 이미지가 없으면 null",
                example = "https://example.com/images/profile/abc.png"
        )
        String profileImageUrl,

        @Schema(
                description = "현재 로그인한 사용자 기준 관계 상태",
                example = "FRIEND"
        )
        RelationshipStatus relationshipStatus
) {
    public static FriendshipResponse from(
            Friendship friendship,
            Member member,
            String profileImageUrl,
            RelationshipStatus relationshipStatus
    ) {
        return new FriendshipResponse(
                friendship.getFriendshipId(),
                member.getMemberId(),
                member.getNickname(),
                profileImageUrl,
                relationshipStatus
        );
    }
}
