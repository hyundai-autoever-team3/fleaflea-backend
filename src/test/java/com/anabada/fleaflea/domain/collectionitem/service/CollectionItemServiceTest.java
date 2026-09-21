package com.anabada.fleaflea.domain.collectionitem.service;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.collection.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.image.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionItemServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long COLLECTION_ITEM_ID = 10L;

    @Mock
    private CollectionItemRepository collectionItemRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ImageService imageService;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private BegRequestRepository begRequestRepository;
    @Mock
    private CollectionTradeRequestRepository collectionTradeRequestRepository;
    @Mock
    private TradeRequestRepository tradeRequestRepository;
    @InjectMocks
    private CollectionItemService service;

    private CollectionItem collectionItem;

    @BeforeEach
    void setUp() {
        Member owner = MemberFixture.createMember(MEMBER_ID);
        collectionItem = CollectionItem.create(
                owner,
                "도감 아이템",
                "설명",
                "collection/image.jpg",
                true
        );
        ReflectionTestUtils.setField(
                collectionItem,
                "collectionItemId",
                COLLECTION_ITEM_ID
        );

        when(collectionItemRepository.findById(COLLECTION_ITEM_ID))
                .thenReturn(Optional.of(collectionItem));
    }

    @Test
    @DisplayName("종료되거나 대기 중인 도감 거래와 구걸 요청을 정리한 뒤 도감 아이템을 삭제한다")
    void deletesRequestsBeforeDeletingCollectionItem() {
        service.deleteCollectionItem(MEMBER_ID, COLLECTION_ITEM_ID);

        verify(collectionTradeRequestRepository)
                .deleteAllDeletableByCollectionItemId(
                        COLLECTION_ITEM_ID,
                        List.of(
                                TradeRequestStatus.PENDING,
                                TradeRequestStatus.REJECTED,
                                TradeRequestStatus.CANCELLED
                        ),
                        TradeRequestStatus.COMPLETED,
                        CollectionTradeType.RENTAL
                );
        verify(begRequestRepository)
                .deleteAllByCollectionItemIdAndStatusIn(
                        COLLECTION_ITEM_ID,
                        List.of(
                                BegRequestStatus.PENDING,
                                BegRequestStatus.REJECTED,
                                BegRequestStatus.CANCELLED
                        )
                );
        verify(collectionItemRepository).delete(collectionItem);
        verify(imageService).deleteAfterCommit("collection/image.jpg");
    }
}
