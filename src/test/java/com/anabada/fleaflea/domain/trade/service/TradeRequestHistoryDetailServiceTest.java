package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.dto.TradeRequestHistoryDetailResponse;
import com.anabada.fleaflea.domain.trade.exception.InvalidTradeRequestTypeException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestAccessDeniedException;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;
import com.anabada.fleaflea.fixture.CollectionItemFixture;
import com.anabada.fleaflea.fixture.ItemFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.ImageService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeRequestHistoryDetailServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long REQUESTER_ID = 2L;

    @Mock
    private TradeRequestRepository tradeRequestRepository;
    @Mock
    private CollectionTradeRequestRepository collectionTradeRequestRepository;
    @Mock
    private BegRequestRepository begRequestRepository;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private ImageService imageService;

    @InjectMocks
    private TradeRequestListService tradeRequestListService;

    private Member owner;
    private Member requester;

    @BeforeEach
    void setUp() {
        owner = MemberFixture.createMember(OWNER_ID);
        requester = MemberFixture.createMember(REQUESTER_ID);
        lenient().when(imageService.getUrl(anyString()))
                .thenAnswer(invocation -> "https://image/" + invocation.getArgument(0));
    }

    @Test
    @DisplayName("판매 거래 요청의 상세 이력을 조회한다")
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
        when(tradeRequestRepository.findWithDetailsByTradeRequestId(100L))
                .thenReturn(Optional.of(request));

        TradeRequestHistoryDetailResponse result =
                tradeRequestListService.getTradeRequestHistoryDetail(OWNER_ID, "ITEM", 100L);

        assertThat(result.requestType()).isEqualTo("ITEM");
        assertThat(result.targetItemTitle()).isEqualTo("판매 물건");
        assertThat(result.message()).isEqualTo("직거래 가능할까요?");
        assertThat(result.owner().memberId()).isEqualTo(OWNER_ID);
        assertThat(result.requester().memberId()).isEqualTo(REQUESTER_ID);
    }

    @Test
    @DisplayName("교환 거래 이력에 제안한 물건을 포함한다")
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
        when(collectionTradeRequestRepository.findWithDetailsByCollectionTradeRequestId(200L))
                .thenReturn(Optional.of(request));

        TradeRequestHistoryDetailResponse result =
                tradeRequestListService.getTradeRequestHistoryDetail(REQUESTER_ID, "collection", 200L);

        assertThat(result.requestType()).isEqualTo("COLLECTION");
        assertThat(result.targetItemDescription()).isEqualTo("대여할 물건");
        assertThat(result.tradeType()).isEqualTo("EXCHANGE");
        assertThat(result.offerItem()).isNotNull();
        assertThat(result.offerItem().title()).isEqualTo("키보드");
        assertThat(result.offerItem().imageUrl())
                .isEqualTo("https://image/offer.png");
    }

    @Test
    @DisplayName("나눔 요청의 상세 이력을 조회한다")
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
                tradeRequestListService.getTradeRequestHistoryDetail(OWNER_ID, "BEG", 300L);

        assertThat(result.requestType()).isEqualTo("BEG");
        assertThat(result.targetItemTitle()).isEqualTo("텀블러");
        assertThat(result.message()).isEqualTo("소중하게 사용하겠습니다.");
        assertThat(result.status().name()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("거래 당사자가 아니면 이력 조회를 거절한다")
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

        assertThatThrownBy(() -> tradeRequestListService.getTradeRequestHistoryDetail(99L, "BEG", 400L))
                .isInstanceOf(TradeRequestAccessDeniedException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRADE_REQUEST_ACCESS_DENIED);
    }

    @Test
    @DisplayName("지원하지 않는 거래 유형이면 조회를 거절한다")
    void rejectsUnsupportedRequestType() {
        assertThatThrownBy(() -> tradeRequestListService.getTradeRequestHistoryDetail(OWNER_ID, "UNKNOWN", 1L))
                .isInstanceOf(InvalidTradeRequestTypeException.class)
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
        CollectionItem item = CollectionItemFixture.createCollectionItemWithId(id, itemOwner, title, true);
        item.update(null, description, null);
        item.updateImageKey(imageKey);
        return item;
    }
}
