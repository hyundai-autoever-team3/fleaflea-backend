package com.anabada.fleaflea.domain.itemcomment.dto;

import com.anabada.fleaflea.domain.itemcomment.domain.ItemComment;
import com.anabada.fleaflea.domain.member.domain.Member;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record ItemCommentResponse(
        Long commentId,
        Long itemId,
        Long parentId,
        Long authorId,
        String nickname,
        boolean seller,
        boolean mine,
        String content,
        long replyCount,
        LocalDateTime createdAt
) {

    public static ItemCommentResponse from(
            ItemComment comment,
            Member author,
            Long sellerId,
            Long viewerId,
            long replyCount
    ) {
        return ItemCommentResponse.builder()
                .commentId(comment.getCommentId())
                .itemId(comment.getItemId())
                .parentId(comment.getParentId())
                .authorId(comment.getAuthorId())
                .nickname(author == null ? "탈퇴한 회원" : author.getNickname())
                .seller(comment.getAuthorId().equals(sellerId))
                .mine(comment.getAuthorId().equals(viewerId))
                .content(comment.getContent())
                .replyCount(replyCount)
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
