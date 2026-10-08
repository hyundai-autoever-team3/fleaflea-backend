package com.anabada.fleaflea.domain.collectionitem.service;

import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItemStatus;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemTradeInProgressException;
import com.anabada.fleaflea.domain.collectionitem.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.fixture.CollectionItemFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
@Transactional
class CollectionItemIntegrationTest {

    @Autowired
    private CollectionItemService collectionItemService;

    @Autowired
    private CollectionItemRepository collectionItemRepository;

    @Autowired
    private CollectionTradeRequestRepository collectionTradeRequestRepository;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private NotificationSseService notificationSseService;

    private Member owner;
    private Member requester;
    private CollectionItem target;
    private CollectionItem offer;

    @BeforeEach
    void setUp() {
        owner = memberRepository.save(MemberFixture.createMember("owner"));
        requester = memberRepository.save(MemberFixture.createMember("requester"));
        target = collectionItemRepository.save(CollectionItemFixture.createCollectionItem(owner, "대상", true));
        offer = collectionItemRepository.save(CollectionItemFixture.createCollectionItem(requester, "제안", true));
    }

    @Test
    @DisplayName("수락된 교환의 대상과 제안 물건 모두 거래 중으로 조회된다")
    void getCollectionItem_acceptedExchange_marksBothItemsInProgress() {
        CollectionTradeRequest collectionTradeRequest = CollectionTradeRequest.create(
                target, requester, offer, CollectionTradeType.EXCHANGE
        );
        collectionTradeRequest.accept();
        collectionTradeRequestRepository.save(collectionTradeRequest);

        assertThat(collectionItemService.getCollectionItem(owner.getMemberId(), target.getCollectionItemId()).status())
                .isEqualTo(CollectionItemStatus.IN_PROGRESS);
        assertThat(collectionItemService.getCollectionItem(requester.getMemberId(), offer.getCollectionItemId()).status())
                .isEqualTo(CollectionItemStatus.IN_PROGRESS);
    }

    @ParameterizedTest(name = "거래 상태={0}")
    @EnumSource(value = TradeRequestStatus.class, names = {"PENDING", "ACCEPTED"})
    @DisplayName("대기 또는 수락된 교환 요청이 있으면 대상과 제안 물건을 삭제할 수 없다")
    void deleteCollectionItem_activeExchange_blocksBothItems(TradeRequestStatus status) {
        CollectionTradeRequest collectionTradeRequest = CollectionTradeRequest.create(
                target, requester, offer, CollectionTradeType.EXCHANGE
        );
        if (status == TradeRequestStatus.ACCEPTED) {
            collectionTradeRequest.accept();
        }
        collectionTradeRequestRepository.save(collectionTradeRequest);

        assertThatThrownBy(() -> collectionItemService.deleteCollectionItem(owner.getMemberId(), target.getCollectionItemId()))
                .isInstanceOf(CollectionItemTradeInProgressException.class);
        assertThatThrownBy(() -> collectionItemService.deleteCollectionItem(requester.getMemberId(), offer.getCollectionItemId()))
                .isInstanceOf(CollectionItemTradeInProgressException.class);

        assertThat(collectionItemRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("취소된 교환 요청을 정리한 뒤 제안 물건을 삭제할 수 있다")
    void deleteCollectionItem_cancelledExchange_removesRequestAndItem() {
        CollectionTradeRequest collectionTradeRequest = CollectionTradeRequest.create(
                target, requester, offer, CollectionTradeType.EXCHANGE
        );
        collectionTradeRequest.cancel();
        collectionTradeRequestRepository.save(collectionTradeRequest);

        collectionItemService.deleteCollectionItem(requester.getMemberId(), offer.getCollectionItemId());
        collectionItemRepository.flush();

        assertThat(collectionItemRepository.existsById(offer.getCollectionItemId())).isFalse();
        assertThat(collectionItemRepository.existsById(target.getCollectionItemId())).isTrue();
        assertThat(collectionTradeRequestRepository.count()).isZero();
    }
}
