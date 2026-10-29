package com.anabada.fleaflea.domain.member;

import com.anabada.fleaflea.domain.chat.domain.ChatMessage;
import com.anabada.fleaflea.domain.chat.domain.ChatRoom;
import com.anabada.fleaflea.domain.chat.security.ChatWebSocketSessionDecorator;
import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.collectionitem.service.CollectionItemService;
import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import com.anabada.fleaflea.domain.item.service.ItemService;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberWithdrawalBlockedException;
import com.anabada.fleaflea.domain.member.repository.MemberWithdrawalRepository;
import com.anabada.fleaflea.domain.member.service.MemberWithdrawalService;
import com.anabada.fleaflea.domain.notification.domain.Notification;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.repository.PushDeviceRepository;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.domain.refreshtoken.repository.RedisRefreshTokenRepository;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.Trade;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.fixture.CollectionItemFixture;
import com.anabada.fleaflea.fixture.ItemFixture;
import com.anabada.fleaflea.fixture.MarketFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.config.JpaAuditingConfig;
import com.anabada.fleaflea.global.config.QueryDslConfig;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class,
        PostgresTestContainerConfiguration.class, MemberWithdrawalRepository.class,
        MemberWithdrawalService.class, ItemService.class, CollectionItemService.class})
class MemberWithdrawalIntegrationTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private MemberWithdrawalRepository withdrawalRepository;
    @Autowired private MemberWithdrawalService withdrawalService;
    @Autowired private ItemService itemService;
    @Autowired private CollectionItemService collectionItemService;
    @Autowired private PushDeviceRepository pushDevices;
    @MockitoBean private ImageService imageService;
    @MockitoBean private RedisRefreshTokenRepository refreshTokens;
    @MockitoBean private ChatWebSocketSessionDecorator sessions;
    @MockitoBean private NotificationSseService sse;

    @Test
    @DisplayName("상품 이미지를 조회하며 삭제된 이미지의 미참조 여부도 검사한다")
    void imageReferenceChecksUseCorrectEntity() {
        Member seller = persistMember("seller");
        Market market = entityManager.persist(MarketFixture.createMarket(seller));
        Item item = entityManager.persistAndFlush(ItemFixture.createItem(market, seller, "상품"));

        assertThat(withdrawalRepository.isImageReferenced(item.getImageKey())).isTrue();
        assertThat(withdrawalRepository.isImageReferenced("items/unused.png")).isFalse();
    }

    @Test
    @DisplayName("소장품 요청 ID와 일반 요청 ID가 같아도 다른 회원의 거래는 삭제하지 않는다")
    void deleteTradesDoesNotMixRequestIdNamespaces() {
        Member withdrawing = persistMember("withdrawing");
        Member seller = persistMember("seller");
        Member buyer = persistMember("buyer");
        Market market = entityManager.persist(MarketFixture.createMarket(seller));
        Item item = entityManager.persist(ItemFixture.createItem(market, seller, "타인 상품"));
        TradeRequest ordinaryRequest = entityManager.persistAndFlush(
                TradeRequest.create(item, buyer, "구매", null, null));
        Trade unrelated = entityManager.persistAndFlush(Trade.create(ordinaryRequest));
        CollectionItem collection = entityManager.persist(
                CollectionItemFixture.createCollectionItem(seller, "소장품", true));
        CollectionTradeRequest request = entityManager.persistAndFlush(
                CollectionTradeRequest.create(collection, withdrawing, null, CollectionTradeType.RENTAL));

        // 두 독립적인 요청 테이블에서 동일한 숫자 ID를 의도적으로 만든다.
        entityManager.getEntityManager().createNativeQuery("""
                update collection_trade_requests set collection_trade_request_id = :collision
                where collection_trade_request_id = :original
                """)
                .setParameter("collision", ordinaryRequest.getTradeRequestId())
                .setParameter("original", request.getCollectionTradeRequestId())
                .executeUpdate();
        Trade related = entityManager.persistAndFlush(Trade.ofCollectionTrade(
                ordinaryRequest.getTradeRequestId(), withdrawing.getMemberId(), seller.getMemberId()));

        withdrawalRepository.deleteTrades(withdrawing.getMemberId());
        entityManager.clear();

        assertThat(entityManager.find(Trade.class, unrelated.getTradeId())).isNotNull();
        assertThat(entityManager.find(Trade.class, related.getTradeId())).isNull();
    }

    @Test
    @DisplayName("탈퇴 시 거래와 개인 데이터는 삭제하고 다른 회원 및 채팅은 남긴다")
    void withdrawalDeletesRelatedDataAndPreservesChat() {
        Member withdrawing = persistMember("withdrawing");
        Member other = persistMember("other");
        Market market = entityManager.persist(MarketFixture.createMarket(other));
        Item item = entityManager.persist(ItemFixture.createItem(market, withdrawing, "내 상품"));
        CollectionItem collection = entityManager.persist(
                CollectionItemFixture.createCollectionItem(withdrawing, "내 소장품", true));
        TradeRequest request = entityManager.persist(TradeRequest.create(item, other, "구매", null, null));
        request.accept(withdrawing.getMemberId());
        request.confirmCompletion(other.getMemberId());
        Trade trade = entityManager.persist(Trade.create(request));
        Notification notification = entityManager.persist(Notification.create(other,
                NotificationType.TRADE_COMPLETED, NotificationReferenceType.ITEM_TRADE_REQUEST,
                request.getTradeRequestId(), "완료"));
        Friendship friendship = entityManager.persist(Friendship.create(
                withdrawing, other, FriendshipStatus.ACCEPTED));
        ChatRoom room = entityManager.persist(ChatRoom.create(withdrawing.getMemberId(), other.getMemberId()));
        ChatMessage message = entityManager.persistAndFlush(ChatMessage.create(
                room.getId(), withdrawing.getMemberId(), "대화 보존", UUID.randomUUID().toString()));
        String imageKey = item.getImageKey();

        withdrawalService.withdraw(withdrawing.getMemberId());
        entityManager.clear();

        assertThat(entityManager.find(Member.class, withdrawing.getMemberId())).isNull();
        assertThat(entityManager.find(Member.class, other.getMemberId())).isNotNull();
        assertThat(entityManager.find(Item.class, item.getItemId())).isNull();
        assertThat(entityManager.find(CollectionItem.class, collection.getCollectionItemId())).isNull();
        assertThat(entityManager.find(TradeRequest.class, request.getTradeRequestId())).isNull();
        assertThat(entityManager.find(Trade.class, trade.getTradeId())).isNull();
        assertThat(entityManager.find(Notification.class, notification.getNotificationId())).isNull();
        assertThat(entityManager.find(Friendship.class, friendship.getFriendshipId())).isNull();
        assertThat(entityManager.find(ChatRoom.class, room.getId())).isNotNull();
        assertThat(entityManager.find(ChatMessage.class, message.getId())).isNotNull();
        verify(imageService).deleteAfterCommit(imageKey);
    }

    @Test
    @DisplayName("회원 탈퇴 시 푸시 기기를 삭제하고 다른 회원의 기기는 보존한다")
    void withdrawalDeletesOnlyWithdrawingMemberPushDevices() {
        Member withdrawing = persistMember("withdrawing-push");
        Member other = persistMember("other-push");
        pushDevices.upsert(withdrawing.getMemberId(), UUID.randomUUID().toString(), "WEB", UUID.randomUUID());
        pushDevices.upsert(other.getMemberId(), UUID.randomUUID().toString(), "WEB", UUID.randomUUID());
        assertThat(pushDevices.countByMember_MemberId(withdrawing.getMemberId())).isEqualTo(1);

        withdrawalService.withdraw(withdrawing.getMemberId());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Member.class, withdrawing.getMemberId())).isNull();
        assertThat(pushDevices.countByMember_MemberId(withdrawing.getMemberId())).isZero();
        assertThat(pushDevices.countByMember_MemberId(other.getMemberId())).isEqualTo(1);
    }

    @Test
    @DisplayName("수락된 거래가 있으면 탈퇴를 막고 데이터를 삭제하지 않는다")
    void withdrawalRejectsAcceptedTrade() {
        Member withdrawing = persistMember("withdrawing");
        Member other = persistMember("other");
        Market market = entityManager.persist(MarketFixture.createMarket(other));
        Item item = entityManager.persist(ItemFixture.createItem(market, withdrawing, "거래 중"));
        TradeRequest request = entityManager.persist(TradeRequest.create(item, other, "구매", null, null));
        request.accept(withdrawing.getMemberId());
        entityManager.flush();

        assertThatThrownBy(() -> withdrawalService.withdraw(withdrawing.getMemberId()))
                .isInstanceOf(MemberWithdrawalBlockedException.class);
        assertThat(entityManager.find(Member.class, withdrawing.getMemberId())).isNotNull();
        assertThat(entityManager.find(Item.class, item.getItemId())).isNotNull();
    }

    private Member persistMember(String suffix) {
        return entityManager.persist(MemberFixture.createMember(suffix));
    }

    @Test
    @DisplayName("반납 완료 상품 삭제는 거래 기록을 요청보다 먼저 삭제한다")
    void deleteReturnedRentalItemDoesNotViolateTradeForeignKey() {
        Member seller = persistMember("seller");
        Member buyer = persistMember("buyer");
        Market market = entityManager.persist(MarketFixture.createMarket(seller));
        Item item = entityManager.persist(ItemFixture.createItem(
                market, seller, "대여 상품", ItemTradeType.RENTAL, ItemStatus.AVAILABLE));
        TradeRequest request = entityManager.persist(TradeRequest.create(
                item, buyer, "대여", LocalDate.now(), LocalDate.now().plusDays(1)));
        request.accept(seller.getMemberId());
        request.confirmCompletion(buyer.getMemberId());
        Trade trade = entityManager.persistAndFlush(Trade.create(request));

        itemService.deleteItem(item.getItemId(), seller.getMemberId());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Item.class, item.getItemId())).isNull();
        assertThat(entityManager.find(TradeRequest.class, request.getTradeRequestId())).isNull();
        assertThat(entityManager.find(Trade.class, trade.getTradeId())).isNull();
    }

    @Test
    @DisplayName("반납 완료 소장품 삭제는 거래 기록을 요청보다 먼저 삭제한다")
    void deleteReturnedCollectionRentalDoesNotViolateTradeForeignKey() {
        Member owner = persistMember("owner");
        Member requester = persistMember("requester");
        CollectionItem collection = entityManager.persist(
                CollectionItemFixture.createCollectionItem(owner, "대여 소장품", true));
        CollectionTradeRequest request = entityManager.persist(CollectionTradeRequest.create(
                collection, requester, null, CollectionTradeType.RENTAL));
        request.complete();
        Trade trade = entityManager.persistAndFlush(Trade.ofCollectionTrade(
                request.getCollectionTradeRequestId(), requester.getMemberId(), owner.getMemberId()));

        collectionItemService.deleteCollectionItem(owner.getMemberId(), collection.getCollectionItemId());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(CollectionItem.class, collection.getCollectionItemId())).isNull();
        assertThat(entityManager.find(CollectionTradeRequest.class, request.getCollectionTradeRequestId())).isNull();
        assertThat(entityManager.find(Trade.class, trade.getTradeId())).isNull();
    }
}
