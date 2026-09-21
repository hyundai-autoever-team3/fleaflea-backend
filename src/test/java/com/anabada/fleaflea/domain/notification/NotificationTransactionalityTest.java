package com.anabada.fleaflea.domain.notification;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.repository.ItemRepository;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.domain.Notification;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.repository.NotificationRepository;
import com.anabada.fleaflea.domain.trade.dto.request.TradeRequestCreateRequest;
import com.anabada.fleaflea.domain.trade.service.TradeRequestService;
import com.anabada.fleaflea.fixture.ItemFixture;
import com.anabada.fleaflea.fixture.MarketFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class NotificationTransactionalityTest {

    @Autowired
    private TradeRequestService tradeRequestService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private MarketMemberRepository marketMemberRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    @DisplayName("거래 요청 시 알림이 같은 트랜잭션 안에서 저장된다")
    void createTradeRequest_savesNotificationInSameTransaction() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member buyer = memberRepository.save(MemberFixture.createMember("buyer"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "테스트 상품"));

        marketMemberRepository.save(MarketMember.create(market, buyer));

        tradeRequestService.createTradeRequest(
                item.getItemId(),
                buyer.getMemberId(),
                createRequest("구매하고 싶어요")
        );

        List<Notification> notifications = notificationsOf(seller);

        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().getType())
                .isEqualTo(NotificationType.TRADE_REQUESTED);
        assertThat(notifications.getFirst().getReferenceType())
                .isEqualTo(NotificationReferenceType.ITEM_TRADE_REQUEST);
        assertThat(notifications.getFirst().getMessage())
                .contains("테스트 상품");
    }

    @Test
    @DisplayName("요청 수락 시 자동 거절된 나머지 요청자들에게도 알림이 간다")
    void acceptTradeRequest_notifiesAutoRejectedRequesters() {
        Member seller = memberRepository.save(MemberFixture.createMember("seller"));
        Member winner = memberRepository.save(MemberFixture.createMember("winner"));
        Member loserA = memberRepository.save(MemberFixture.createMember("loserA"));
        Member loserB = memberRepository.save(MemberFixture.createMember("loserB"));

        Market market = marketRepository.save(MarketFixture.createMarket(seller));
        Item item = itemRepository.save(ItemFixture.createItem(market, seller, "테스트 상품"));

        marketMemberRepository.save(MarketMember.create(market, winner));
        marketMemberRepository.save(MarketMember.create(market, loserA));
        marketMemberRepository.save(MarketMember.create(market, loserB));

        Long winnerRequestId = tradeRequestService.createTradeRequest(
                item.getItemId(), winner.getMemberId(), createRequest("제가 살게요")
        ).tradeRequestId();

        tradeRequestService.createTradeRequest(
                item.getItemId(), loserA.getMemberId(), createRequest("저도요")
        );
        tradeRequestService.createTradeRequest(
                item.getItemId(), loserB.getMemberId(), createRequest("저도 살래요")
        );

        tradeRequestService.acceptTradeRequest(winnerRequestId, seller.getMemberId());

        assertThat(typesOf(winner))
                .containsExactly(NotificationType.TRADE_ACCEPTED);

        for (Member loser : List.of(loserA, loserB)) {
            List<Notification> received = notificationsOf(loser);

            assertThat(received).hasSize(1);
            assertThat(received.getFirst().getType())
                    .isEqualTo(NotificationType.TRADE_REJECTED);
            assertThat(received.getFirst().getMessage())
                    .contains("다른 분과 거래되어");
        }
    }

    private List<Notification> notificationsOf(Member member) {
        return notificationRepository
                .findAllByReceiver_MemberId(member.getMemberId(), PageRequest.of(0, 10))
                .getContent();
    }

    private List<NotificationType> typesOf(Member member) {
        return notificationsOf(member).stream()
                .map(Notification::getType)
                .toList();
    }

    private TradeRequestCreateRequest createRequest(String message) {
        TradeRequestCreateRequest request = mock(TradeRequestCreateRequest.class);

        when(request.message()).thenReturn(message);
        when(request.rentalStartDate()).thenReturn(null);
        when(request.rentalEndDate()).thenReturn(null);

        return request;
    }
}
