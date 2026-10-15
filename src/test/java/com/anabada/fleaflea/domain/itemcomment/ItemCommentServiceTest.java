package com.anabada.fleaflea.domain.itemcomment;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.exception.ItemNotFoundException;
import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentCreateRequest;
import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentResponse;
import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentUpdateRequest;
import com.anabada.fleaflea.domain.itemcomment.service.ItemCommentService;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.exception.MarketNotParticipantException;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.fixture.ItemFixture;
import com.anabada.fleaflea.fixture.MarketFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.dto.PageResponse;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import com.anabada.fleaflea.support.RedisTestContainerConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
@Import(RedisTestContainerConfiguration.class)
@Transactional
class ItemCommentServiceTest {

    @Autowired
    private ItemCommentService commentService;

    @Autowired
    private EntityManager entityManager;

    private Member seller;
    private Member viewer;
    private Member outsider;
    private Item item;
    private Item otherItem;

    @BeforeEach
    void setUp() {
        seller = persist(MemberFixture.createMember("comment-seller"));
        viewer = persist(MemberFixture.createMember("comment-viewer"));
        outsider = persist(MemberFixture.createMember("comment-outsider"));
        Market market = persist(MarketFixture.createMarket(seller));
        persist(MarketMember.create(market, seller));
        persist(MarketMember.create(market, viewer));
        item = persist(ItemFixture.createItem(market, seller, "에어팟4"));
        otherItem = persist(ItemFixture.createItem(market, seller, "다른 상품"));
    }

    @Test
    @DisplayName("댓글 작성 후 작성자와 판매자 여부 및 시각을 반환한다")
    void createsCommentWithAuthor() {
        ItemCommentResponse response = create(item, seller, null, "  직거래 가능합니다  ");
        assertThat(response.content()).isEqualTo("직거래 가능합니다");
        assertThat(response.nickname()).isEqualTo(seller.getNickname());
        assertThat(response.seller()).isTrue();
        assertThat(response.mine()).isTrue();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("일반 댓글과 대댓글을 별도 페이지로 조회하고 답글 수를 반환한다")
    void paginatesRootsAndReplies() {
        ItemCommentResponse first = create(item, viewer, null, "첫 질문");
        ItemCommentResponse second = create(item, viewer, null, "둘째 질문");
        create(item, seller, first.commentId(), "판매자 답변");
        create(item, viewer, first.commentId(), "추가 질문");
        create(otherItem, viewer, null, "다른 상품 질문");

        PageResponse<ItemCommentResponse> roots = find(item, seller, null, 0, 1);
        assertThat(roots.totalElements()).isEqualTo(2);
        assertThat(roots.hasNext()).isTrue();
        assertThat(roots.content()).extracting(ItemCommentResponse::commentId).containsExactly(first.commentId());
        assertThat(roots.content().getFirst().replyCount()).isEqualTo(2);
        assertThat(find(item, seller, null, 1, 1).content())
                .extracting(ItemCommentResponse::commentId).containsExactly(second.commentId());

        PageResponse<ItemCommentResponse> replies = find(item, viewer, first.commentId(), 0, 1);
        assertThat(replies.totalElements()).isEqualTo(2);
        assertThat(replies.content().getFirst().seller()).isTrue();
        assertThat(replies.content().getFirst().mine()).isFalse();
    }

    @Test
    @DisplayName("미참여자는 조회·작성·수정·삭제할 수 없다")
    void rejectsOutsider() {
        ItemCommentResponse comment = create(item, viewer, null, "질문");
        assertThatThrownBy(() -> find(item, outsider, null, 0, 10))
                .isInstanceOf(MarketNotParticipantException.class);
        assertThatThrownBy(() -> create(item, outsider, null, "질문"))
                .isInstanceOf(MarketNotParticipantException.class);
        assertThatThrownBy(() -> commentService.updateComment(item.getItemId(), comment.commentId(),
                outsider.getMemberId(), new ItemCommentUpdateRequest("변경")))
                .isInstanceOf(MarketNotParticipantException.class);
        assertThatThrownBy(() -> commentService.deleteComment(item.getItemId(), comment.commentId(), outsider.getMemberId()))
                .isInstanceOf(MarketNotParticipantException.class);
    }

    @Test
    @DisplayName("판매자와 마켓 개설자도 다른 작성자의 댓글을 변경할 수 없다")
    void rejectsOtherAuthorIncludingHost() {
        ItemCommentResponse comment = create(item, viewer, null, "질문");
        assertThatThrownBy(() -> commentService.updateComment(item.getItemId(), comment.commentId(),
                seller.getMemberId(), new ItemCommentUpdateRequest("변경")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_COMMENT_NOT_OWNER);
        assertThatThrownBy(() -> commentService.deleteComment(item.getItemId(), comment.commentId(), seller.getMemberId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_COMMENT_NOT_OWNER);
    }

    @Test
    @DisplayName("본인 댓글과 대댓글의 내용을 수정할 수 있다")
    void updatesOwnComments() {
        ItemCommentResponse root = create(item, viewer, null, "질문");
        ItemCommentResponse reply = create(item, viewer, root.commentId(), "추가 질문");
        for (ItemCommentResponse comment : new ItemCommentResponse[]{root, reply}) {
            ItemCommentResponse updated = commentService.updateComment(item.getItemId(), comment.commentId(),
                    viewer.getMemberId(), new ItemCommentUpdateRequest("  변경 내용  "));
            assertThat(updated.content()).isEqualTo("변경 내용");
            assertThat(updated.createdAt()).isEqualTo(comment.createdAt());
            assertThat(updated.updatedAt()).isAfterOrEqualTo(comment.updatedAt());
        }
    }

    @Test
    @DisplayName("다른 상품의 부모 댓글로 답글을 작성하거나 조회할 수 없다")
    void rejectsParentFromOtherItem() {
        ItemCommentResponse parent = create(otherItem, viewer, null, "질문");
        assertThatThrownBy(() -> create(item, viewer, parent.commentId(), "답글"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_COMMENT_NOT_FOUND);
        assertThatThrownBy(() -> find(item, viewer, parent.commentId(), 0, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_COMMENT_NOT_FOUND);
        assertThatThrownBy(() -> commentService.deleteComment(item.getItemId(), parent.commentId(), viewer.getMemberId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("대댓글에 다시 답글을 작성하거나 대댓글 목록을 조회할 수 없다")
    void rejectsNestedReply() {
        ItemCommentResponse root = create(item, viewer, null, "질문");
        ItemCommentResponse reply = create(item, seller, root.commentId(), "답글");
        assertThatThrownBy(() -> create(item, viewer, reply.commentId(), "재답글"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_COMMENT_INVALID_PARENT);
        assertThatThrownBy(() -> find(item, viewer, reply.commentId(), 0, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_COMMENT_INVALID_PARENT);
    }

    @Test
    @DisplayName("부모 삭제 시 내용과 작성자를 숨기고 답글은 유지한다")
    void preservesRepliesAfterParentDeletion() {
        ItemCommentResponse root = create(item, viewer, null, "민감한 질문");
        create(item, seller, root.commentId(), "답변");
        commentService.deleteComment(item.getItemId(), root.commentId(), viewer.getMemberId());
        entityManager.flush();
        entityManager.clear();

        ItemCommentResponse deleted = find(item, seller, null, 0, 10).content().getFirst();
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.content()).isEqualTo("삭제된 댓글입니다");
        assertThat(deleted.authorId()).isNull();
        assertThat(deleted.nickname()).isNull();
        assertThat(deleted.replyCount()).isEqualTo(1);
        assertThat(find(item, seller, root.commentId(), 0, 10).content())
                .extracting(ItemCommentResponse::content).containsExactly("답변");
        assertThat(entityManager.createNativeQuery("select content from item_comments where comment_id = :id")
                .setParameter("id", root.commentId()).getSingleResult()).isEqualTo("");
    }

    @Test
    @DisplayName("삭제된 부모는 수정하거나 새 답글을 작성할 수 없다")
    void rejectsDeletedParentMutation() {
        ItemCommentResponse root = create(item, viewer, null, "질문");
        commentService.deleteComment(item.getItemId(), root.commentId(), viewer.getMemberId());
        assertThatThrownBy(() -> create(item, seller, root.commentId(), "답글"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_COMMENT_INVALID_PARENT);
        assertThatThrownBy(() -> commentService.updateComment(item.getItemId(), root.commentId(),
                viewer.getMemberId(), new ItemCommentUpdateRequest("복구")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("마지막 답글까지 삭제하면 삭제된 부모도 목록에서 제외된다")
    void hidesThreadWithoutActiveReplies() {
        ItemCommentResponse root = create(item, viewer, null, "질문");
        ItemCommentResponse reply = create(item, seller, root.commentId(), "답변");
        commentService.deleteComment(item.getItemId(), root.commentId(), viewer.getMemberId());
        commentService.deleteComment(item.getItemId(), reply.commentId(), seller.getMemberId());
        assertThat(find(item, viewer, null, 0, 10).totalElements()).isZero();
        assertThat(find(item, viewer, root.commentId(), 0, 10).totalElements()).isZero();
    }

    @Test
    @DisplayName("답글 없는 댓글 삭제는 반복 호출해도 성공하고 목록에서 제외된다")
    void deletesWithoutRepliesIdempotently() {
        ItemCommentResponse comment = create(item, viewer, null, "질문");
        commentService.deleteComment(item.getItemId(), comment.commentId(), viewer.getMemberId());
        commentService.deleteComment(item.getItemId(), comment.commentId(), viewer.getMemberId());
        assertThat(find(item, viewer, null, 0, 10).content()).isEmpty();
    }

    @Test
    @DisplayName("상품을 삭제하면 댓글과 대댓글도 DB에서 함께 제거된다")
    void cascadesItemDeletion() {
        ItemCommentResponse root = create(item, viewer, null, "질문");
        create(item, seller, root.commentId(), "답변");
        entityManager.flush();
        entityManager.createNativeQuery("delete from items where item_id = :id")
                .setParameter("id", item.getItemId()).executeUpdate();
        entityManager.clear();
        assertThat(((Number) entityManager.createNativeQuery("select count(*) from item_comments where item_id = :id")
                .setParameter("id", item.getItemId()).getSingleResult()).longValue()).isZero();
        assertThatThrownBy(() -> find(item, viewer, null, 0, 10)).isInstanceOf(ItemNotFoundException.class);
    }

    private ItemCommentResponse create(Item target, Member author, Long parentId, String content) {
        return commentService.createComment(target.getItemId(), author.getMemberId(),
                new ItemCommentCreateRequest(content, parentId));
    }

    private PageResponse<ItemCommentResponse> find(Item target, Member member, Long parentId, int page, int size) {
        return commentService.findComments(target.getItemId(), member.getMemberId(), parentId, page, size);
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
