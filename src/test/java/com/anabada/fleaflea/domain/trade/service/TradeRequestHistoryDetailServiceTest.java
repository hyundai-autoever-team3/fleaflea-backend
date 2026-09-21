package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.dto.TradeRequestHistoryDetailResponse;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import com.anabada.fleaflea.fixture.ItemFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TradeRequestHistoryDetailServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long REQUESTER_ID = 2L;

    @Mock
    private TradeRequestRepository itemRequests;
    @Mock
    private CollectionTradeRequestRepository collectionRequests;
    @Mock
    private BegRequestRepository begRequestRepository;
    @Mock
    private TradeRepository trades;
    @Mock
    private ImageService images;

    @InjectMocks
    private TradeRequestListService service;

    private Member owner;
    private Member requester;

    @BeforeEach
    void setUp() {
        owner = MemberFixture.createMember(OWNER_ID);
        requester = MemberFixture.createMember(REQUESTER_ID);
        lenient().when(images.getUrl(anyString()))
                .thenAnswer(invocation -> "https://image/" + invocation.getArgument(0));
    }

    @Test
    void returnsItemRequestDetail() {
        Market market = Market.create(
                owner, "플리마켓", "설명", null, "invite"
        );
        Item item = ItemFixture.createItem(market, owner, "판매 물건");
        ReflectionTestUtils.setField(item, "itemId", 10L);

        TradeRequest request = TradeRequest.create(
                item,
                requester,
                "직거래 가능할까요?",
                null,
                null
        );
        ReflectionTestUtils.setField(request, "tradeRequestId", 100L);
        when(itemRequests.findWithDetailsByTradeRequestId(100L))
                .thenReturn(Optional.of(request));

        TradeRequestHistoryDetailResponse result =
                service.detail(OWNER_ID, "ITEM", 100L);

        assertThat(result.requestType()).isEqualTo("ITEM");
        assertThat(result.targetItemTitle()).isEqualTo("판매 물건");
        assertThat(result.message()).isEqualTo("직거래 가능할까요?");
        assertThat(result.owner().memberId()).isEqualTo(OWNER_ID);
        assertThat(result.requester().memberId()).isEqualTo(REQUESTER_ID);
    }

    @Test
    void returnsCollectionExchangeDetailWithOffer() {
        CollectionItem target = collectionItem(
                20L, owner, "노트북 케이스", "대여할 물건", "target.png"
        );
        CollectionItem offer = collectionItem(
                21L, requester, "키보드", "교환 제안 물건", "offer.png"
        );
        CollectionTradeRequest request = CollectionTradeRequest.create(
                target,
                requester,
                offer,
                CollectionTradeType.EXCHANGE
        );
        ReflectionTestUtils.setField(
                request,
                "collectionTradeRequestId",
                200L
        );
        when(collectionRequests.findWithDetailsByCollectionTradeRequestId(200L))
                .thenReturn(Optional.of(request));

        TradeRequestHistoryDetailResponse result =
                service.detail(REQUESTER_ID, "collection", 200L);

        assertThat(result.requestType()).isEqualTo("COLLECTION");
        assertThat(result.targetItemDescription()).isEqualTo("대여할 물건");
        assertThat(result.tradeType()).isEqualTo("EXCHANGE");
        assertThat(result.offerItem()).isNotNull();
        assertThat(result.offerItem().title()).isEqualTo("키보드");
        assertThat(result.offerItem().imageUrl())
                .isEqualTo("https://image/offer.png");
    }

    @Test
    void returnsBegRequestDetail() {
        CollectionItem target = collectionItem(
                30L, owner, "텀블러", "구걸 대상", "beg.png"
        );
        BegRequest request = BegRequest.create(
                target,
                requester,
                "소중하게 사용하겠습니다.",
                BegRequestStatus.REJECTED
        );
        ReflectionTestUtils.setField(request, "begRequestId", 300L);
        when(begRequestRepository.findWithDetailsByBegRequestId(300L))
                .thenReturn(Optional.of(request));

        TradeRequestHistoryDetailResponse result =
                service.detail(OWNER_ID, "BEG", 300L);

        assertThat(result.requestType()).isEqualTo("BEG");
        assertThat(result.targetItemTitle()).isEqualTo("텀블러");
        assertThat(result.message()).isEqualTo("소중하게 사용하겠습니다.");
        assertThat(result.status().name()).isEqualTo("REJECTED");
    }

    @Test
    void rejectsMemberWhoIsNotParty() {
        CollectionItem target = collectionItem(
                40L, owner, "도감 물건", "설명", "item.png"
        );
        BegRequest request = BegRequest.create(
                target,
                requester,
                "사연",
                BegRequestStatus.PENDING
        );
        ReflectionTestUtils.setField(request, "begRequestId", 400L);
        when(begRequestRepository.findWithDetailsByBegRequestId(400L))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.detail(99L, "BEG", 400L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRADE_REQUEST_ACCESS_DENIED);
    }

    @Test
    void rejectsUnsupportedRequestType() {
        assertThatThrownBy(() -> service.detail(OWNER_ID, "UNKNOWN", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRADE_REQUEST_INVALID_TYPE);
    }

    private CollectionItem collectionItem(
            Long id,
            Member itemOwner,
            String title,
            String description,
            String imageKey
    ) {
        CollectionItem item = CollectionItem.create(
                itemOwner,
                title,
                description,
                imageKey,
                true
        );
        ReflectionTestUtils.setField(item, "collectionItemId", id);
        return item;
    }
}
