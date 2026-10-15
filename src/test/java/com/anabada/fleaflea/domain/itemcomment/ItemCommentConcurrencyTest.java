package com.anabada.fleaflea.domain.itemcomment;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.repository.ItemRepository;
import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentCreateRequest;
import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentResponse;
import com.anabada.fleaflea.domain.itemcomment.service.ItemCommentService;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.fixture.ItemFixture;
import com.anabada.fleaflea.fixture.MarketFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import com.anabada.fleaflea.support.RedisTestContainerConfiguration;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
@Import(RedisTestContainerConfiguration.class)
class ItemCommentConcurrencyTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemCommentService commentService;

    @Test
    @DisplayName("부모 삭제가 먼저 상품 락을 잡으면 대기하던 답글 작성은 삭제 후 거절된다")
    void rejectsReplyAfterConcurrentParentDeletion() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        long[] ids = transaction.execute(status -> {
            Member author = MemberFixture.createMember("race-" + UUID.randomUUID());
            entityManager.persist(author);
            Market market = MarketFixture.createMarket(author);
            entityManager.persist(market);
            entityManager.persist(MarketMember.create(market, author));
            Item item = ItemFixture.createItem(market, author, "동시 삭제 검증");
            entityManager.persist(item);
            ItemCommentResponse root = commentService.createComment(item.getItemId(), author.getMemberId(),
                    new ItemCommentCreateRequest("질문", null));
            return new long[]{item.getItemId(), author.getMemberId(), root.commentId()};
        });
        CountDownLatch parentDeleted = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        CountDownLatch replyStarted = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> deletion = executor.submit(() -> transaction.executeWithoutResult(status -> {
                itemRepository.findLockedByItemId(ids[0]).orElseThrow();
                commentService.deleteComment(ids[0], ids[2], ids[1]);
                parentDeleted.countDown();
                await(releaseDelete);
            }));
            try {
                assertThat(parentDeleted.await(10, TimeUnit.SECONDS)).isTrue();
                Future<ErrorCode> reply = executor.submit(() -> {
                    replyStarted.countDown();
                    try {
                        commentService.createComment(ids[0], ids[1], new ItemCommentCreateRequest("답변", ids[2]));
                        return null;
                    } catch (BusinessException exception) {
                        return exception.getErrorCode();
                    }
                });
                assertThat(replyStarted.await(10, TimeUnit.SECONDS)).isTrue();
                releaseDelete.countDown();
                deletion.get(10, TimeUnit.SECONDS);
                assertThat(reply.get(10, TimeUnit.SECONDS)).isEqualTo(ErrorCode.ITEM_COMMENT_INVALID_PARENT);
                assertThat(commentService.findComments(ids[0], ids[1], ids[2], 0, 10).totalElements()).isZero();
            } finally {
                releaseDelete.countDown();
            }
        } finally {
            transaction.executeWithoutResult(status -> {
                entityManager.createNativeQuery("delete from items where item_id = :id")
                        .setParameter("id", ids[0]).executeUpdate();
                entityManager.createNativeQuery("delete from market_members where member_id = :id")
                        .setParameter("id", ids[1]).executeUpdate();
                entityManager.createNativeQuery("delete from markets where host_id = :id")
                        .setParameter("id", ids[1]).executeUpdate();
                entityManager.createNativeQuery("delete from members where member_id = :id")
                        .setParameter("id", ids[1]).executeUpdate();
            });
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 요청 대기 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
