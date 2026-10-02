package com.anabada.fleaflea.domain.collectionitem.service;

import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemNotFoundException;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemTradeInProgressException;
import com.anabada.fleaflea.domain.collectionitem.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.dto.CollectionTradeRequestCreateRequest;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.service.CollectionTradeService;
import com.anabada.fleaflea.fixture.CollectionItemFixture;
import com.anabada.fleaflea.fixture.FriendshipFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
@Sql(statements = "TRUNCATE TABLE friendships, members RESTART IDENTITY CASCADE", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "TRUNCATE TABLE friendships, members RESTART IDENTITY CASCADE", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CollectionItemConcurrencyTest {

    @Autowired
    private CollectionItemService collectionItemService;

    @Autowired
    private CollectionTradeService collectionTradeService;

    @Autowired
    private CollectionItemRepository collectionItemRepository;

    @Autowired
    private CollectionTradeRequestRepository collectionTradeRequestRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private NotificationSseService notificationSseService;

    private Member owner;
    private Member requester;
    private CollectionItem target;

    @BeforeEach
    void setUp() {
        owner = memberRepository.save(MemberFixture.createMember("owner"));
        requester = memberRepository.save(MemberFixture.createMember("requester"));
        target = collectionItemRepository.save(CollectionItemFixture.createCollectionItem(owner, "대여 대상", true));
        friendshipRepository.save(FriendshipFixture.createFriendship(owner, requester, FriendshipStatus.ACCEPTED));
    }

    @Test
    @DisplayName("도감 삭제와 거래 생성이 동시에 실행돼도 요청이 남은 물건은 삭제되지 않는다")
    void deleteCollectionItem_concurrentTradeCreation_preservesConsistency() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> deletion = executor.submit(() -> {
                awaitStart(ready, start);
                try {
                    collectionItemService.deleteCollectionItem(owner.getMemberId(), target.getCollectionItemId());
                    return true;
                } catch (CollectionItemTradeInProgressException exception) {
                    return false;
                }
            });
            Future<Boolean> creation = executor.submit(() -> {
                awaitStart(ready, start);
                try {
                    collectionTradeService.createCollectionTradeRequest(requester.getMemberId(), target.getCollectionItemId(),
                            new CollectionTradeRequestCreateRequest(CollectionTradeType.RENTAL, null));
                    return true;
                } catch (CollectionItemNotFoundException exception) {
                    return false;
                }
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();

            start.countDown();
            boolean deleted = deletion.get(10, TimeUnit.SECONDS);
            boolean created = creation.get(10, TimeUnit.SECONDS);

            assertThat(deleted ^ created).isTrue();
            assertThat(collectionItemRepository.existsById(target.getCollectionItemId())).isEqualTo(created);
            assertThat(collectionTradeRequestRepository.count()).isEqualTo(created ? 1 : 0);
        }
    }

    private void awaitStart(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("도감 요청 시작 신호를 받지 못했습니다.");
        }
    }
}
