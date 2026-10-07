package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.collectionitem.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.event.TradeCompletedEvent;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import com.anabada.fleaflea.domain.notification.notifier.TradeNotifier;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.fixture.CollectionItemFixture;
import com.anabada.fleaflea.fixture.CollectionTradeFixture;
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeAccessDeniedException;
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeOwnershipChangedException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionTradeServiceTest {

    private static final Long REQUEST_ID = 30L;
    private static final Long OWNER_ID = 1L;
    private static final Long REQUESTER_ID = 2L;

    @Mock
    private CollectionTradeRequestRepository collectionTradeRequestRepository;
    @Mock
    private CollectionItemRepository collectionItemRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private TradeNotifier tradeNotifier;

    @InjectMocks
    private CollectionTradeService collectionTradeService;

    private CollectionTradeRequest collectionTradeRequest;
    private Member owner;
    private Member requester;
    private CollectionItem target;
    private CollectionItem offer;

    @BeforeEach
    void setUp() {
        owner = MemberFixture.createMember(OWNER_ID);
        requester = MemberFixture.createMember(REQUESTER_ID);

        target = CollectionItemFixture.createCollectionItemWithId(10L, owner, "교환 대상", true);
        offer = CollectionItemFixture.createCollectionItemWithId(20L, requester, "제안 아이템", true);
        collectionTradeRequest = CollectionTradeFixture.createAcceptedRequestWithId(
                REQUEST_ID, target, requester, offer, CollectionTradeType.EXCHANGE
        );

        when(collectionTradeRequestRepository.findLockedByCollectionTradeRequestId(REQUEST_ID))
                .thenReturn(Optional.of(collectionTradeRequest));
    }

    @Test
    @DisplayName("도감 교환 요청자는 수락된 거래를 완료할 수 있다")
    void requesterCompletesAcceptedCollectionTrade() {
        when(tradeRepository.existsByCollectionTradeRequestId(REQUEST_ID)).thenReturn(false);
        when(collectionItemRepository.findAllByIdForUpdate(List.of(10L, 20L))).thenReturn(List.of(target, offer));

        collectionTradeService.completeCollectionTradeRequest(REQUESTER_ID, REQUEST_ID);

        assertThat(collectionTradeRequest.getStatus()).isEqualTo(TradeRequestStatus.COMPLETED);
        assertThat(target.getOwner()).isSameAs(requester);
        assertThat(offer.getOwner()).isSameAs(owner);
        verify(tradeRepository).save(any());

        ArgumentCaptor<TradeCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(TradeCompletedEvent.class);
        verify(tradeNotifier).notifyOf(eventCaptor.capture());

        TradeCompletedEvent event = eventCaptor.getValue();
        assertThat(event.confirmerId()).isEqualTo(REQUESTER_ID);
        assertThat(event.counterpartyId()).isEqualTo(OWNER_ID);
        assertThat(event.confirmerNickname()).isEqualTo(collectionTradeRequest.getRequester().getNickname());
    }

    @Test
    @DisplayName("도감 아이템 소유자는 거래 완료를 처리할 수 없다")
    void ownerCannotCompleteCollectionTrade() {
        assertThatThrownBy(() -> collectionTradeService.completeCollectionTradeRequest(OWNER_ID, REQUEST_ID))
                .isInstanceOfSatisfying(CollectionTradeAccessDeniedException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED));

        assertThat(collectionTradeRequest.getStatus()).isEqualTo(TradeRequestStatus.ACCEPTED);
        verify(tradeRepository, never()).save(any());
        verifyNoInteractions(tradeNotifier);
    }

    @Test
    @DisplayName("도감 대여 거래를 완료해도 아이템 소유권은 유지된다")
    void rentalCompletionKeepsOwnership() {
        CollectionTradeRequest rentalRequest = CollectionTradeFixture.createAcceptedRequestWithId(
                REQUEST_ID, target, requester, null, CollectionTradeType.RENTAL);
        when(collectionTradeRequestRepository.findLockedByCollectionTradeRequestId(REQUEST_ID))
                .thenReturn(Optional.of(rentalRequest));
        when(tradeRepository.existsByCollectionTradeRequestId(REQUEST_ID)).thenReturn(false);

        collectionTradeService.completeCollectionTradeRequest(REQUESTER_ID, REQUEST_ID);

        assertThat(rentalRequest.getStatus()).isEqualTo(TradeRequestStatus.COMPLETED);
        assertThat(target.getOwner()).isSameAs(owner);
        verify(collectionItemRepository, never()).findAllByIdForUpdate(any());
    }

    @Test
    @DisplayName("교환 완료 전에 제안 아이템 소유권이 바뀌면 완료할 수 없다")
    void cannotCompleteWhenOfferedItemOwnershipChanged() {
        Member other = MemberFixture.createMember(3L);
        offer.transferTo(other);
        when(tradeRepository.existsByCollectionTradeRequestId(REQUEST_ID)).thenReturn(false);
        when(collectionItemRepository.findAllByIdForUpdate(List.of(10L, 20L))).thenReturn(List.of(target, offer));

        assertThatThrownBy(() -> collectionTradeService.completeCollectionTradeRequest(REQUESTER_ID, REQUEST_ID))
                .isInstanceOfSatisfying(CollectionTradeOwnershipChangedException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.COLLECTION_TRADE_OWNERSHIP_CHANGED));

        assertThat(collectionTradeRequest.getStatus()).isEqualTo(TradeRequestStatus.ACCEPTED);
        assertThat(target.getOwner()).isSameAs(owner);
        verify(tradeRepository, never()).save(any());
        verifyNoInteractions(tradeNotifier);
    }
}
