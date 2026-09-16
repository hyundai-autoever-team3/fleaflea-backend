package com.anabada.fleaflea.domain.item.dto;

import com.anabada.fleaflea.domain.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "상품 판매자 응답")
public record SellerResponse(
        @Schema(description = "판매자 회원 ID", example = "2")
        Long id,

        @Schema(description = "판매자 닉네임", example = "플리유저")
        String nickname,

        @Schema(description = "판매자 프로필 이미지 URL. 이미지가 없으면 null",
                example = "https://example.com/profiles/profile.png")
        String profileImageUrl
) {
    public static SellerResponse from(Member member, String profileImageUrl) {
        return SellerResponse.builder()
                .id(member.getMemberId())
                .nickname(member.getNickname())
                .profileImageUrl(profileImageUrl)
                .build();
    }
}
