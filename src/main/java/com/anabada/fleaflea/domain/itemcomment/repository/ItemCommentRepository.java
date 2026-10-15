package com.anabada.fleaflea.domain.itemcomment.repository;

import com.anabada.fleaflea.domain.itemcomment.domain.ItemComment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemCommentRepository extends JpaRepository<ItemComment, Long> {

    Optional<ItemComment> findByCommentIdAndItemId(Long commentId, Long itemId);

    @Query("""
            select comment from ItemComment comment
            where comment.itemId = :itemId and comment.parentId is null
              and (comment.deleted = false or exists (
                  select reply.commentId from ItemComment reply
                  where reply.parentId = comment.commentId and reply.deleted = false
              ))
            """)
    Page<ItemComment> findRoots(@Param("itemId") Long itemId, Pageable pageable);

    Page<ItemComment> findByItemIdAndParentIdAndDeletedFalse(
            Long itemId,
            Long parentId,
            Pageable pageable
    );

    @Query("""
            select comment.parentId as parentId, count(comment) as replyCount
            from ItemComment comment
            where comment.parentId in :parentIds and comment.deleted = false
            group by comment.parentId
            """)
    List<ReplyCount> countReplies(@Param("parentIds") Collection<Long> parentIds);

    interface ReplyCount {
        Long getParentId();
        long getReplyCount();
    }
}
