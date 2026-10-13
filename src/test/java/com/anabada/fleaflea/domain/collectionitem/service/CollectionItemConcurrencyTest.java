package com.anabada.fleaflea.domain.collectionitem.service;

import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemResponse;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemUpdateRequest;
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
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @RepeatedTest(5)
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

    @RepeatedTest(5)
    @DisplayName("도감 이름·설명·공개 여부를 동시에 수정해도 모든 변경이 보존된다")
    void updateCollectionItem_concurrentPartialChanges_preservesAllChanges() throws Exception {
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            Future<CollectionItemResponse> titleChange = executor.submit(() -> {
                awaitStart(ready, start);
                return collectionItemService.updateCollectionItem(
                        owner.getMemberId(), target.getCollectionItemId(),
                        new CollectionItemUpdateRequest("변경된 이름", null, null, null)
                );
            });
            Future<CollectionItemResponse> descriptionChange = executor.submit(() -> {
                awaitStart(ready, start);
                return collectionItemService.updateCollectionItem(
                        owner.getMemberId(), target.getCollectionItemId(),
                        new CollectionItemUpdateRequest(null, "변경된 설명", null, null)
                );
            });
            Future<CollectionItemResponse> visibilityChange = executor.submit(() -> {
                awaitStart(ready, start);
                return collectionItemService.updateCollectionItem(
                        owner.getMemberId(), target.getCollectionItemId(),
                        new CollectionItemUpdateRequest(null, null, false, null)
                );
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();

            start.countDown();
            titleChange.get(10, TimeUnit.SECONDS);
            descriptionChange.get(10, TimeUnit.SECONDS);
            visibilityChange.get(10, TimeUnit.SECONDS);

            CollectionItem updatedCollectionItem = collectionItemRepository.findById(target.getCollectionItemId())
                    .orElseThrow(CollectionItemNotFoundException::new);
            assertThat(updatedCollectionItem.getTitle()).isEqualTo("변경된 이름");
            assertThat(updatedCollectionItem.getDescription()).isEqualTo("변경된 설명");
            assertThat(updatedCollectionItem.getIsPublic()).isFalse();
        }
    }

    @ParameterizedTest(name = "선행 수정 롤백={0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("다른 트랜잭션의 도감 수정은 선행 수정의 커밋 또는 롤백까지 기다린다")
    void updateCollectionItem_outerTransaction_holdsLockUntilCompletion(boolean rollback) throws Exception {
        CountDownLatch updateStarted = new CountDownLatch(1);
        AtomicInteger updatingBackendId = new AtomicInteger();

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<CollectionItemResponse> descriptionChange = new TransactionTemplate(transactionManager).execute(status -> {
                collectionItemService.updateCollectionItem(
                        owner.getMemberId(), target.getCollectionItemId(),
                        new CollectionItemUpdateRequest("커밋할 이름", null, null, null)
                );
                Future<CollectionItemResponse> pendingUpdate = submitDescriptionChange(
                        executor, updateStarted, updatingBackendId
                );
                assertWaitingForDatabaseLock(updateStarted, updatingBackendId, pendingUpdate);

                if (rollback) {
                    status.setRollbackOnly();
                }

                return pendingUpdate;
            });

            assertThat(descriptionChange).isNotNull();
            descriptionChange.get(10, TimeUnit.SECONDS);

            CollectionItem updatedCollectionItem = collectionItemRepository.findById(target.getCollectionItemId())
                    .orElseThrow(CollectionItemNotFoundException::new);
            assertThat(updatedCollectionItem.getTitle()).isEqualTo(rollback ? target.getTitle() : "커밋할 이름");
            assertThat(updatedCollectionItem.getDescription()).isEqualTo("후속 수정 설명");
        }
    }

    @Test
    @DisplayName("도감 삭제를 기다리던 수정은 삭제 커밋 후 도감 없음 예외로 종료된다")
    void updateCollectionItem_concurrentDeletion_rejectsUpdateAfterDeleteCommit() throws Exception {
        CountDownLatch updateStarted = new CountDownLatch(1);
        AtomicInteger updatingBackendId = new AtomicInteger();

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<CollectionItemResponse> descriptionChange = new TransactionTemplate(transactionManager).execute(status -> {
                collectionItemService.deleteCollectionItem(owner.getMemberId(), target.getCollectionItemId());
                Future<CollectionItemResponse> pendingUpdate = submitDescriptionChange(
                        executor, updateStarted, updatingBackendId
                );
                assertWaitingForDatabaseLock(updateStarted, updatingBackendId, pendingUpdate);

                return pendingUpdate;
            });

            assertThat(descriptionChange).isNotNull();
            assertThatThrownBy(() -> descriptionChange.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(CollectionItemNotFoundException.class);
            assertThat(collectionItemRepository.existsById(target.getCollectionItemId())).isFalse();
        }
    }

    @RepeatedTest(5)
    @DisplayName("도감을 동시에 두 번 삭제하면 한 요청만 성공한다")
    void deleteCollectionItem_concurrentDeletions_deletesOnce() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstDeletion = executor.submit(() -> deleteAfterStart(ready, start));
            Future<Boolean> secondDeletion = executor.submit(() -> deleteAfterStart(ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();

            start.countDown();
            assertThat(firstDeletion.get(10, TimeUnit.SECONDS) ^ secondDeletion.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(collectionItemRepository.existsById(target.getCollectionItemId())).isFalse();
        }
    }

    private Future<CollectionItemResponse> submitDescriptionChange(
            ExecutorService executor,
            CountDownLatch updateStarted,
            AtomicInteger updatingBackendId
    ) {
        return executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            Integer backendId = jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class);
            assertThat(backendId).isNotNull();
            updatingBackendId.set(backendId);
            updateStarted.countDown();

            return collectionItemService.updateCollectionItem(
                    owner.getMemberId(), target.getCollectionItemId(),
                    new CollectionItemUpdateRequest(null, "후속 수정 설명", null, null)
            );
        }));
    }

    private void assertWaitingForDatabaseLock(
            CountDownLatch updateStarted,
            AtomicInteger updatingBackendId,
            Future<CollectionItemResponse> pendingUpdate
    ) {
        try {
            assertThat(updateStarted.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("후속 도감 수정의 시작을 기다리다 중단되었습니다.", exception);
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbcTemplate.queryForObject(
                    "select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, updatingBackendId.get()
            );
            if (Boolean.TRUE.equals(waiting)) {
                assertThat(pendingUpdate.isDone()).isFalse();
                return;
            }

            assertThat(pendingUpdate.isDone())
                    .as("선행 트랜잭션이 종료되기 전에 후속 도감 수정이 완료되면 안 됩니다.")
                    .isFalse();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }

        throw new AssertionError("후속 도감 수정의 PostgreSQL 락 대기를 확인하지 못했습니다.");
    }

    private boolean deleteAfterStart(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        awaitStart(ready, start);
        try {
            collectionItemService.deleteCollectionItem(owner.getMemberId(), target.getCollectionItemId());
            return true;
        } catch (CollectionItemNotFoundException exception) {
            return false;
        }
    }

    private void awaitStart(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("도감 요청 시작 신호를 받지 못했습니다.");
        }
    }
}
