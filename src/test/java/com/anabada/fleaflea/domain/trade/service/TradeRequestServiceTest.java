package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import com.anabada.fleaflea.domain.item.repository.ItemRepository;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.exception.MarketNotParticipantException;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.dto.request.TradeRequestCreateRequest;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestAlreadyExistsException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestItemNotAvailableException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestSelfRequestException;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;
import com.anabada.fleaflea.fixture.ItemFixture;
import com.anabada.fleaflea.fixture.MarketFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class TradeRequestServiceTest {
    // 비관적 락, 트랜잭션, 상태 전이를 함께 검증하기 위해 서비스 통합 테스트로 진행

    @Autowired
    private TradeRequestService tradeRequestService;

    @Autowired
    private TradeRequestRepository tradeRequestRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private MarketMemberRepository marketMemberRepository;

    @Autowired
    private EntityManager entityManager; // bulk update 사용하고 있어서 추가

    @Test
    @DisplayName("플리마켓 참여자는 거래 요청을 생성할 수 있다")
    void createTradeRequest() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "테스트 상품"));

        joinMarket(market, buyer);

        TradeRequestCreateRequest request = createRequest("구매하고 싶어요");

        tradeRequestService.createTradeRequest(
                item.getItemId(),
                buyer.getMemberId(),
                request
        );

        boolean exists = tradeRequestRepository
                .existsByItem_ItemIdAndRequester_MemberIdAndStatus(
                        item.getItemId(),
                        buyer.getMemberId(),
                        TradeRequestStatus.PENDING
                );

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("플리마켓 참여자가 아니면 거래 요청을 생성할 수 없다")
    void createTradeRequest_notMarketParticipant() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "테스트 상품"));

        TradeRequestCreateRequest request = createRequest("구매하고 싶어요");

        assertThatThrownBy(() ->
                tradeRequestService.createTradeRequest(
                        item.getItemId(),
                        buyer.getMemberId(),
                        request
                )
        ).isInstanceOf(MarketNotParticipantException.class);
    }

    @Test
    @DisplayName("자신의 상품에는 거래 요청을 생성할 수 없다")
    void createTradeRequest_selfRequest() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "테스트 상품"));

        joinMarket(market, seller);

        TradeRequestCreateRequest request = createRequest("구매하고 싶어요");

        assertThatThrownBy(() ->
                tradeRequestService.createTradeRequest(
                        item.getItemId(),
                        seller.getMemberId(),
                        request
                )
        ).isInstanceOf(TradeRequestSelfRequestException.class);
    }

    @Test
    @DisplayName("거래 가능한 상태가 아닌 상품에는 거래 요청을 생성할 수 없다")
    void createTradeRequest_itemNotAvailable() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = ItemFixture.createItem(market, seller, "테스트 상품");

        item.startTrade();
        itemRepository.save(item);

        joinMarket(market, buyer);

        TradeRequestCreateRequest request = createRequest("구매하고 싶어요");

        assertThatThrownBy(() ->
                tradeRequestService.createTradeRequest(
                        item.getItemId(),
                        buyer.getMemberId(),
                        request
                )
        ).isInstanceOf(TradeRequestItemNotAvailableException.class);
    }

    @Test
    @DisplayName("같은 구매자는 동일 상품에 PENDING 거래 요청을 중복 생성할 수 없다")
    void createTradeRequest_duplicate() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "테스트 상품"));

        joinMarket(market, buyer);

        TradeRequestCreateRequest request = createRequest("구매하고 싶어요");

        tradeRequestService.createTradeRequest(
                item.getItemId(),
                buyer.getMemberId(),
                request
        );

        assertThatThrownBy(() ->
                tradeRequestService.createTradeRequest(
                        item.getItemId(),
                        buyer.getMemberId(),
                        request
                )
        ).isInstanceOf(TradeRequestAlreadyExistsException.class);
    }
    @Test
    @DisplayName("판매자가 거래 요청을 수락하면 요청은 ACCEPTED, 나머지 요청은 REJECTED, 상품은 IN_PROGRESS가 된다")
    void acceptTradeRequest() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer1 = memberRepository.save(MemberFixture.createMember("buyer1"));
        Member buyer2 = memberRepository.save(MemberFixture.createMember("buyer2"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(
                ItemFixture.createItem(market, seller, "테스트 상품")
        );

        TradeRequest request1 = tradeRequestRepository.save(
                TradeRequest.create(item, buyer1, "거래 요청 1", null, null)
        );

        TradeRequest request2 = tradeRequestRepository.save(
                TradeRequest.create(item, buyer2, "거래 요청 2", null, null)
        );

        // 테스트 준비 데이터를 DB에 확실히 저장하고 1차 캐시를 비운다
        entityManager.flush();
        entityManager.clear();

        tradeRequestService.acceptTradeRequest(
                request1.getTradeRequestId(),
                seller.getMemberId()
        );

        // rejectOtherPendingRequests() bulk update 결과를 실제 DB에서 다시 조회
        entityManager.flush();
        entityManager.clear();

        TradeRequest acceptedRequest = tradeRequestRepository.findById(
                request1.getTradeRequestId()
        ).orElseThrow();

        TradeRequest rejectedRequest = tradeRequestRepository.findById(
                request2.getTradeRequestId()
        ).orElseThrow();

        Item resultItem = itemRepository.findById(
                item.getItemId()
        ).orElseThrow();

        assertThat(acceptedRequest.getStatus())
                .isEqualTo(TradeRequestStatus.ACCEPTED);

        assertThat(rejectedRequest.getStatus())
                .isEqualTo(TradeRequestStatus.REJECTED);

        assertThat(resultItem.getStatus())
                .isEqualTo(ItemStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("판매자가 거래 요청을 거절하면 요청은 REJECTED가 된다")
    void rejectTradeRequest() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "테스트 상품"));

        TradeRequest tradeRequest = tradeRequestRepository.save(
                TradeRequest.create(item, buyer, "거래 요청", null, null)
        );

        tradeRequestService.rejectTradeRequest(
                tradeRequest.getTradeRequestId(),
                seller.getMemberId()
        );

        TradeRequest result = tradeRequestRepository.findById(tradeRequest.getTradeRequestId())
                .orElseThrow();

        Item resultItem = itemRepository.findById(item.getItemId())
                .orElseThrow();

        assertThat(result.getStatus())
                .isEqualTo(TradeRequestStatus.REJECTED);

        assertThat(resultItem.getStatus())
                .isEqualTo(ItemStatus.AVAILABLE);
    }

    @Test
    @DisplayName("구매자가 거래 요청을 취소하면 요청은 CANCELLED가 된다")
    void cancelTradeRequest() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "테스트 상품"));

        TradeRequest tradeRequest = tradeRequestRepository.save(
                TradeRequest.create(item, buyer, "거래 요청", null, null)
        );

        tradeRequestService.cancelTradeRequest(
                tradeRequest.getTradeRequestId(),
                buyer.getMemberId()
        );

        TradeRequest result = tradeRequestRepository.findById(tradeRequest.getTradeRequestId())
                .orElseThrow();

        Item resultItem = itemRepository.findById(item.getItemId())
                .orElseThrow();

        assertThat(result.getStatus())
                .isEqualTo(TradeRequestStatus.CANCELLED);

        assertThat(resultItem.getStatus())
                .isEqualTo(ItemStatus.AVAILABLE);
    }

    @Test
    @DisplayName("구매자가 거래를 완료하면 요청과 상품이 완료되고 거래 이력이 생성된다")
    void confirmTradeRequestCompletion() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "테스트 상품"));

        TradeRequest tradeRequest = tradeRequestRepository.save(
                TradeRequest.create(item, buyer, "거래 요청", null, null)
        );

        tradeRequestService.acceptTradeRequest(
                tradeRequest.getTradeRequestId(),
                seller.getMemberId()
        );

        long tradeCountBefore = tradeRepository.count();

        tradeRequestService.confirmTradeRequestCompletion(
                tradeRequest.getTradeRequestId(),
                buyer.getMemberId()
        );

        TradeRequest result = tradeRequestRepository.findById(tradeRequest.getTradeRequestId())
                .orElseThrow();

        Item resultItem = itemRepository.findById(item.getItemId())
                .orElseThrow();

        long tradeCountAfter = tradeRepository.count();

        assertThat(result.getStatus())
                .isEqualTo(TradeRequestStatus.COMPLETED);

        assertThat(resultItem.getStatus())
                .isEqualTo(ItemStatus.COMPLETED);

        assertThat(tradeCountAfter - tradeCountBefore)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("대여 거래를 완료하면 상품이 다시 거래 가능해지고 재대여 요청을 생성할 수 있다")
    void confirmRentalCompletionMakesItemAvailableAgain() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(
                ItemFixture.createItem(
                        market,
                        seller,
                        "대여 상품",
                        ItemTradeType.RENTAL,
                        ItemStatus.AVAILABLE
                )
        );
        joinMarket(market, buyer);

        LocalDate rentalStartDate = LocalDate.now().plusDays(1);
        LocalDate rentalEndDate = rentalStartDate.plusDays(3);
        TradeRequest tradeRequest = tradeRequestRepository.save(
                TradeRequest.create(
                        item,
                        buyer,
                        "대여 요청",
                        rentalStartDate,
                        rentalEndDate
                )
        );

        tradeRequestService.acceptTradeRequest(
                tradeRequest.getTradeRequestId(),
                seller.getMemberId()
        );
        tradeRequestService.confirmTradeRequestCompletion(
                tradeRequest.getTradeRequestId(),
                buyer.getMemberId()
        );

        TradeRequest completedRequest = tradeRequestRepository.findById(
                tradeRequest.getTradeRequestId()
        ).orElseThrow();
        Item availableItem = itemRepository.findById(item.getItemId())
                .orElseThrow();

        assertThat(completedRequest.getStatus())
                .isEqualTo(TradeRequestStatus.COMPLETED);
        assertThat(availableItem.getStatus())
                .isEqualTo(ItemStatus.AVAILABLE);

        TradeRequestCreateRequest nextRentalRequest = new TradeRequestCreateRequest(
                "다시 대여 요청",
                rentalEndDate.plusDays(1),
                rentalEndDate.plusDays(3)
        );

        tradeRequestService.createTradeRequest(
                item.getItemId(),
                buyer.getMemberId(),
                nextRentalRequest
        );

        assertThat(tradeRequestRepository
                .existsByItem_ItemIdAndRequester_MemberIdAndStatus(
                        item.getItemId(),
                        buyer.getMemberId(),
                        TradeRequestStatus.PENDING
                )).isTrue();
    }

    private TradeRequestCreateRequest createRequest(String message) {
        TradeRequestCreateRequest request = mock(TradeRequestCreateRequest.class);

        when(request.message()).thenReturn(message);
        when(request.rentalStartDate()).thenReturn(null);
        when(request.rentalEndDate()).thenReturn(null);

        return request;
    }

    private void joinMarket(Market market, Member member) {
        marketMemberRepository.save(
                MarketMember.create(market, member)
        );
    }
}
