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

    Page<ItemComment> findByItemIdAndParentIdIsNull(Long itemId, Pageable pageable);

    Page<ItemComment> findByItemIdAndParentId(Long itemId, Long parentId, Pageable pageable);

    @Query("""
            select comment.parentId as parentId, count(comment) as replyCount
            from ItemComment comment
            where comment.parentId in :parentIds
            group by comment.parentId
            """)
    List<ReplyCount> countReplies(@Param("parentIds") Collection<Long> parentIds);

    interface ReplyCount {
        Long getParentId();
        long getReplyCount();
    }
}
