package com.anabada.fleaflea.domain.itemcomment.domain;

import com.anabada.fleaflea.domain.itemcomment.exception.ItemCommentInvalidParentException;
import com.anabada.fleaflea.domain.itemcomment.exception.ItemCommentNotOwnerException;
import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "item_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long authorId;

    private Long parentId;

    @Column(nullable = false, length = 2000)
    private String content;

    @Builder
    private ItemComment(Long itemId, Long authorId, Long parentId, String content) {
        this.itemId = itemId;
        this.authorId = authorId;
        this.parentId = parentId;
        this.content = content;
    }

    public static ItemComment create(
            Long itemId,
            Long authorId,
            Long parentId,
            String content
    ) {
        return ItemComment.builder()
                .itemId(itemId)
                .authorId(authorId)
                .parentId(parentId)
                .content(content)
                .build();
    }

    public void validateReplyParent() {
        if (parentId != null) {
            throw new ItemCommentInvalidParentException();
        }
    }

    public void validateOwner(Long memberId) {
        if (!authorId.equals(memberId)) {
            throw new ItemCommentNotOwnerException();
        }
    }

    public void update(String content) {
        this.content = content;
    }

}
