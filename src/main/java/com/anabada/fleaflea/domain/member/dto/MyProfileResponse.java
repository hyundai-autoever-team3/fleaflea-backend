package com.anabada.fleaflea.domain.member.dto;

import com.anabada.fleaflea.domain.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 프로필 응답")
public record MyProfileResponse(
        @Schema(description = "회원 ID", example = "1")
        Long memberId,

        @Schema(description = "이메일", example = "jongpil@example.com")
        String email,

        @Schema(description = "닉네임", example = "jongpil")
        String nickname,

        @Schema(
                description = "프로필 이미지 URL. 이미지가 없으면 null",
                example = "https://example.com/images/profile/abc.png"
        )
        String profileImageUrl
) {

    public static MyProfileResponse from(Member member, String profileImageUrl) {
        return new MyProfileResponse(
                member.getMemberId(),
                member.getEmail(),
                member.getNickname(),
                profileImageUrl
        );
    }

}
