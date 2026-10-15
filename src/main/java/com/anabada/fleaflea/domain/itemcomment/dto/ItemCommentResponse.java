package com.anabada.fleaflea.domain.itemcomment.dto;

import com.anabada.fleaflea.domain.itemcomment.domain.ItemComment;
import com.anabada.fleaflea.domain.member.domain.Member;
import java.time.LocalDateTime;

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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ItemCommentResponse from(
            ItemComment comment,
            Member author,
            Long sellerId,
            Long viewerId,
            long replyCount
    ) {
        return new ItemCommentResponse(
                comment.getCommentId(),
                comment.getItemId(),
                comment.getParentId(),
                comment.getAuthorId(),
                author == null ? "탈퇴한 회원" : author.getNickname(),
                comment.getAuthorId().equals(sellerId),
                comment.getAuthorId().equals(viewerId),
                comment.getContent(),
                replyCount,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
