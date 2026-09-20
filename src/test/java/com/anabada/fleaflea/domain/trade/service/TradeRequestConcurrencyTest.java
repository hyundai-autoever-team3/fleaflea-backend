package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.repository.ItemRepository;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.dto.request.TradeRequestCreateRequest;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;
import com.anabada.fleaflea.fixture.ItemFixture;
import com.anabada.fleaflea.fixture.MarketFixture;
import com.anabada.fleaflea.fixture.MarketMemberFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class TradeRequestConcurrencyTest {

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

    @Test
    @DisplayName("같은 상품의 거래 요청을 동시에 수락하면 하나만 성공한다")
    void acceptTradeRequest_concurrency() throws Exception {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer1 = memberRepository.save(MemberFixture.createMember("buyer1"));
        Member buyer2 = memberRepository.save(MemberFixture.createMember("buyer2"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "동시성 테스트 상품"));

        TradeRequest request1 = tradeRequestRepository.save(
                TradeRequest.create(item, buyer1, "거래 요청 1", null, null)
        );

        TradeRequest request2 = tradeRequestRepository.save(
                TradeRequest.create(item, buyer2, "거래 요청 2", null, null)
        );

        List<Boolean> results = runConcurrently(
                () -> tradeRequestService.acceptTradeRequest(request1.getTradeRequestId(), seller.getMemberId()),
                () -> tradeRequestService.acceptTradeRequest(request2.getTradeRequestId(), seller.getMemberId())
        );

        long successCount = countSuccess(results);

        List<TradeRequest> tradeRequests = tradeRequestRepository.findAllById(
                List.of(request1.getTradeRequestId(), request2.getTradeRequestId())
        );

        long acceptedCount = tradeRequests.stream()
                .filter(request -> request.getStatus() == TradeRequestStatus.ACCEPTED)
                .count();

        Item resultItem = itemRepository.findById(item.getItemId())
                .orElseThrow();

        assertThat(successCount)
                .as("동시에 수락해도 하나의 요청만 성공해야 한다")
                .isEqualTo(1);

        assertThat(acceptedCount)
                .as("ACCEPTED 상태의 거래 요청은 하나만 존재해야 한다")
                .isEqualTo(1);

        assertThat(resultItem.getStatus())
                .isEqualTo(ItemStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("같은 거래 요청을 동시에 수락하고 거절하면 하나만 성공한다")
    void acceptAndReject_concurrency() throws Exception {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "동시성 테스트 상품"));

        TradeRequest tradeRequest = tradeRequestRepository.save(
                TradeRequest.create(item, buyer, "거래 요청", null, null)
        );

        List<Boolean> results = runConcurrently(
                () -> tradeRequestService.acceptTradeRequest(tradeRequest.getTradeRequestId(), seller.getMemberId()),
                () -> tradeRequestService.rejectTradeRequest(tradeRequest.getTradeRequestId(), seller.getMemberId())
        );

        long successCount = countSuccess(results);

        TradeRequest result = tradeRequestRepository.findById(tradeRequest.getTradeRequestId())
                .orElseThrow();

        Item resultItem = itemRepository.findById(item.getItemId())
                .orElseThrow();

        assertThat(successCount)
                .as("수락과 거절 중 하나만 성공해야 한다")
                .isEqualTo(1);

        assertThat(result.getStatus())
                .isIn(
                        TradeRequestStatus.ACCEPTED,
                        TradeRequestStatus.REJECTED
                );

        if (result.getStatus() == TradeRequestStatus.ACCEPTED) {
            assertThat(resultItem.getStatus())
                    .isEqualTo(ItemStatus.IN_PROGRESS);
        }

        if (result.getStatus() == TradeRequestStatus.REJECTED) {
            assertThat(resultItem.getStatus())
                    .isEqualTo(ItemStatus.AVAILABLE);
        }
    }

    @Test
    @DisplayName("같은 거래 요청을 동시에 수락하고 취소하면 하나만 성공한다")
    void acceptAndCancel_concurrency() throws Exception {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "동시성 테스트 상품"));

        TradeRequest tradeRequest = tradeRequestRepository.save(
                TradeRequest.create(item, buyer, "거래 요청", null, null)
        );

        List<Boolean> results = runConcurrently(
                () -> tradeRequestService.acceptTradeRequest(tradeRequest.getTradeRequestId(), seller.getMemberId()),
                () -> tradeRequestService.cancelTradeRequest(tradeRequest.getTradeRequestId(), buyer.getMemberId())
        );

        long successCount = countSuccess(results);

        TradeRequest result = tradeRequestRepository.findById(tradeRequest.getTradeRequestId())
                .orElseThrow();

        Item resultItem = itemRepository.findById(item.getItemId())
                .orElseThrow();

        assertThat(successCount)
                .as("수락과 취소 중 하나만 성공해야 한다")
                .isEqualTo(1);

        assertThat(result.getStatus())
                .isIn(
                        TradeRequestStatus.ACCEPTED,
                        TradeRequestStatus.CANCELLED
                );

        if (result.getStatus() == TradeRequestStatus.ACCEPTED) {
            assertThat(resultItem.getStatus())
                    .isEqualTo(ItemStatus.IN_PROGRESS);
        }

        if (result.getStatus() == TradeRequestStatus.CANCELLED) {
            assertThat(resultItem.getStatus())
                    .isEqualTo(ItemStatus.AVAILABLE);
        }
    }

    @Test
    @DisplayName("거래 수락과 새로운 거래 요청이 동시에 발생해도 진행 중인 상품에 대기 요청이 남지 않는다")
    void createAndAccept_concurrency() throws Exception {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer1 = memberRepository.save(MemberFixture.createMember("buyer1"));
        Member buyer2 = memberRepository.save(MemberFixture.createMember("buyer2"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "동시성 테스트 상품"));

        joinMarket(market, buyer2);

        TradeRequest request1 = tradeRequestRepository.save(
                TradeRequest.create(item, buyer1, "기존 거래 요청", null, null)
        );

        TradeRequestCreateRequest createRequest = mock(TradeRequestCreateRequest.class);

        when(createRequest.message()).thenReturn("새로운 거래 요청");
        when(createRequest.rentalStartDate()).thenReturn(null);
        when(createRequest.rentalEndDate()).thenReturn(null);

        runConcurrently(
                () -> tradeRequestService.acceptTradeRequest(request1.getTradeRequestId(), seller.getMemberId()),
                () -> tradeRequestService.createTradeRequest(item.getItemId(), buyer2.getMemberId(), createRequest)
        );

        TradeRequest acceptedRequest = tradeRequestRepository.findById(request1.getTradeRequestId())
                .orElseThrow();

        Item resultItem = itemRepository.findById(item.getItemId())
                .orElseThrow();

        boolean pendingExists = tradeRequestRepository
                .existsByItem_ItemIdAndRequester_MemberIdAndStatus(
                        item.getItemId(),
                        buyer2.getMemberId(),
                        TradeRequestStatus.PENDING
                );

        assertThat(acceptedRequest.getStatus())
                .isEqualTo(TradeRequestStatus.ACCEPTED);

        assertThat(resultItem.getStatus())
                .isEqualTo(ItemStatus.IN_PROGRESS);

        assertThat(pendingExists)
                .as("거래 진행 중인 상품에는 새로운 PENDING 요청이 남으면 안 된다")
                .isFalse();
    }

    @Test
    @DisplayName("구매자가 거래 완료를 동시에 요청해도 한 번만 완료된다")
    void confirmCompletion_concurrency() throws Exception {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "동시성 테스트 상품"));

        TradeRequest tradeRequest = tradeRequestRepository.save(
                TradeRequest.create(item, buyer, "거래 요청", null, null)
        );

        tradeRequestService.acceptTradeRequest(
                tradeRequest.getTradeRequestId(),
                seller.getMemberId()
        );

        long tradeCountBefore = tradeRepository.count();

        List<Boolean> results = runConcurrently(
                () -> tradeRequestService.confirmTradeRequestCompletion(
                        tradeRequest.getTradeRequestId(),
                        buyer.getMemberId()
                ),
                () -> tradeRequestService.confirmTradeRequestCompletion(
                        tradeRequest.getTradeRequestId(),
                        buyer.getMemberId()
                )
        );

        long successCount = countSuccess(results);

        TradeRequest result = tradeRequestRepository.findById(tradeRequest.getTradeRequestId())
                .orElseThrow();

        Item resultItem = itemRepository.findById(item.getItemId())
                .orElseThrow();

        long tradeCountAfter = tradeRepository.count();

        assertThat(successCount)
                .as("동시에 완료 요청해도 하나만 성공해야 한다")
                .isEqualTo(1);

        assertThat(result.getStatus())
                .isEqualTo(TradeRequestStatus.COMPLETED);

        assertThat(resultItem.getStatus())
                .isEqualTo(ItemStatus.COMPLETED);

        assertThat(tradeCountAfter - tradeCountBefore)
                .as("Trade는 한 건만 생성되어야 한다")
                .isEqualTo(1);
    }

    private List<Boolean> runConcurrently(Runnable firstTask, Runnable secondTask) throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstResult = executorService.submit(
                    () -> execute(firstTask, readyLatch, startLatch)
            );

            Future<Boolean> secondResult = executorService.submit(
                    () -> execute(secondTask, readyLatch, startLatch)
            );

            readyLatch.await();
            startLatch.countDown();

            return List.of(
                    firstResult.get(),
                    secondResult.get()
            );
        }
    }

    private boolean execute(Runnable task, CountDownLatch readyLatch, CountDownLatch startLatch) {
        readyLatch.countDown();

        try {
            startLatch.await();
            task.run();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private long countSuccess(List<Boolean> results) {
        return results.stream()
                .filter(Boolean::booleanValue)
                .count();
    }

    private void joinMarket(Market market, Member member) {
        marketMemberRepository.save(
                MarketMemberFixture.createMarketMember(market, member)
        );
    }
}