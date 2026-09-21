package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.collection.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.event.TradeCompletedEvent;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionTradeServiceTest {

    private static final Long REQUEST_ID = 30L;
    private static final Long OWNER_ID = 1L;
    private static final Long REQUESTER_ID = 2L;

    @Mock
    private CollectionTradeRequestRepository requests;
    @Mock
    private CollectionItemRepository items;
    @Mock
    private MemberRepository members;
    @Mock
    private FriendshipRepository friendships;
    @Mock
    private TradeRepository trades;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CollectionTradeService service;

    private CollectionTradeRequest request;
    private Member owner;
    private Member requester;
    private CollectionItem target;
    private CollectionItem offer;

    @BeforeEach
    void setUp() {
        owner = MemberFixture.createMember(OWNER_ID);
        requester = MemberFixture.createMember(REQUESTER_ID);

        target = CollectionItem.create(
                owner,
                "교환 대상",
                "교환할 도감 아이템",
                null,
                true
        );
        offer = CollectionItem.create(
                requester,
                "제안 아이템",
                "요청자가 제안한 도감 아이템",
                null,
                true
        );
        ReflectionTestUtils.setField(target, "collectionItemId", 10L);
        ReflectionTestUtils.setField(offer, "collectionItemId", 20L);

        request = CollectionTradeRequest.create(
                target,
                requester,
                offer,
                CollectionTradeType.EXCHANGE
        );
        ReflectionTestUtils.setField(request, "collectionTradeRequestId", REQUEST_ID);
        request.accept();

        when(requests.findLockedByCollectionTradeRequestId(REQUEST_ID))
                .thenReturn(Optional.of(request));
    }

    @Test
    @DisplayName("도감 교환 요청자는 수락된 거래를 완료할 수 있다")
    void requesterCompletesAcceptedCollectionTrade() {
        when(trades.existsByCollectionTradeRequestId(REQUEST_ID)).thenReturn(false);
        when(items.findAllByIdForUpdate(List.of(10L, 20L))).thenReturn(List.of(target, offer));

        service.complete(REQUESTER_ID, REQUEST_ID);

        assertThat(request.getStatus()).isEqualTo(TradeRequestStatus.COMPLETED);
        assertThat(target.getOwner()).isSameAs(requester);
        assertThat(offer.getOwner()).isSameAs(owner);
        verify(trades).save(any());

        ArgumentCaptor<TradeCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(TradeCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TradeCompletedEvent event = eventCaptor.getValue();
        assertThat(event.confirmerId()).isEqualTo(REQUESTER_ID);
        assertThat(event.counterpartyId()).isEqualTo(OWNER_ID);
        assertThat(event.confirmerNickname()).isEqualTo(request.getRequester().getNickname());
    }

    @Test
    @DisplayName("도감 아이템 소유자는 거래 완료를 처리할 수 없다")
    void ownerCannotCompleteCollectionTrade() {
        assertThatThrownBy(() -> service.complete(OWNER_ID, REQUEST_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED));

        assertThat(request.getStatus()).isEqualTo(TradeRequestStatus.ACCEPTED);
        verify(trades, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("도감 대여 거래를 완료해도 아이템 소유권은 유지된다")
    void rentalCompletionKeepsOwnership() {
        CollectionTradeRequest rentalRequest = CollectionTradeRequest.create(
                target,
                requester,
                null,
                CollectionTradeType.RENTAL
        );
        ReflectionTestUtils.setField(rentalRequest, "collectionTradeRequestId", REQUEST_ID);
        rentalRequest.accept();
        when(requests.findLockedByCollectionTradeRequestId(REQUEST_ID))
                .thenReturn(Optional.of(rentalRequest));
        when(trades.existsByCollectionTradeRequestId(REQUEST_ID)).thenReturn(false);

        service.complete(REQUESTER_ID, REQUEST_ID);

        assertThat(rentalRequest.getStatus()).isEqualTo(TradeRequestStatus.COMPLETED);
        assertThat(target.getOwner()).isSameAs(owner);
        verify(items, never()).findAllByIdForUpdate(any());
    }

    @Test
    @DisplayName("교환 완료 전에 제안 아이템 소유권이 바뀌면 완료할 수 없다")
    void cannotCompleteWhenOfferedItemOwnershipChanged() {
        Member other = MemberFixture.createMember(3L);
        offer.transferTo(other);
        when(trades.existsByCollectionTradeRequestId(REQUEST_ID)).thenReturn(false);
        when(items.findAllByIdForUpdate(List.of(10L, 20L))).thenReturn(List.of(target, offer));

        assertThatThrownBy(() -> service.complete(REQUESTER_ID, REQUEST_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.COLLECTION_TRADE_OWNERSHIP_CHANGED));

        assertThat(request.getStatus()).isEqualTo(TradeRequestStatus.ACCEPTED);
        assertThat(target.getOwner()).isSameAs(owner);
        verify(trades, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
