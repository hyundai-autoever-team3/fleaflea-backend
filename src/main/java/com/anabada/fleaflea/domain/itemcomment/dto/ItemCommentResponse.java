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
        boolean deleted,
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
        boolean deleted = comment.isDeleted();
        String nickname = null;
        if (!deleted) {
            nickname = author == null ? "탈퇴한 회원" : author.getNickname();
        }
        return new ItemCommentResponse(
                comment.getCommentId(),
                comment.getItemId(),
                comment.getParentId(),
                deleted ? null : comment.getAuthorId(),
                nickname,
                !deleted && comment.getAuthorId().equals(sellerId),
                !deleted && comment.getAuthorId().equals(viewerId),
                deleted ? "삭제된 댓글입니다" : comment.getContent(),
                deleted,
                replyCount,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
