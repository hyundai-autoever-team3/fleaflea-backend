package com.anabada.fleaflea.domain.collectionitem.service;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItemStatus;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemCreateRequest;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemResponse;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemUpdateRequest;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemAccessDeniedException;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemNotFoundException;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemTradeInProgressException;
import com.anabada.fleaflea.domain.collectionitem.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;
import com.anabada.fleaflea.fixture.CollectionItemFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.ImageCategory;
import com.anabada.fleaflea.global.image.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionItemServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ITEM_ID = 10L;

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
    private CollectionItemService collectionItemService;

    private Member owner;
    private CollectionItem collectionItem;

    @BeforeEach
    void setUp() {
        owner = MemberFixture.createMember(MEMBER_ID);
        collectionItem = CollectionItemFixture.createCollectionItemWithId(ITEM_ID, owner, "도감 아이템", true);
    }

    @Test
    @DisplayName("도감을 등록하면 트랜잭션에 연결된 이미지 키를 저장한다")
    void createCollectionItem_savesUploadedImageKey() {
        MockMultipartFile image = new MockMultipartFile("image", new byte[]{1});
        CollectionItemCreateRequest request = new CollectionItemCreateRequest("텀블러", "설명", true, image);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(owner));
        when(imageService.uploadInTransaction(image, ImageCategory.COLLECTION_ITEM)).thenReturn("image-key");
        when(collectionItemRepository.save(any(CollectionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageService.getUrl("image-key")).thenReturn("https://example.test/image.png");

        CollectionItemResponse response = collectionItemService.createCollectionItem(MEMBER_ID, request);

        assertThat(response.title()).isEqualTo("텀블러");
        assertThat(response.ownerId()).isEqualTo(MEMBER_ID);
        assertThat(response.imageUrl()).isEqualTo("https://example.test/image.png");
        verify(imageService).uploadInTransaction(image, ImageCategory.COLLECTION_ITEM);
    }

    @Test
    @DisplayName("소유자는 자신의 비공개 도감도 상세 조회할 수 있다")
    void getCollectionItem_owner_canReadPrivateItem() {
        collectionItem.update(null, null, false);
        prepareDetail(MEMBER_ID);

        CollectionItemResponse response = collectionItemService.getCollectionItem(MEMBER_ID, ITEM_ID);

        assertThat(response.isPublic()).isFalse();
        verifyNoInteractions(friendshipRepository);
    }

    @Test
    @DisplayName("역방향으로 수락된 친구도 공개 도감을 조회할 수 있다")
    void getCollectionItem_reverseFriendship_allowsRead() {
        prepareDetail(2L);
        when(friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                2L, MEMBER_ID, FriendshipStatus.ACCEPTED)).thenReturn(false);
        when(friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                MEMBER_ID, 2L, FriendshipStatus.ACCEPTED)).thenReturn(true);

        CollectionItemResponse response = collectionItemService.getCollectionItem(2L, ITEM_ID);

        assertThat(response.collectionItemId()).isEqualTo(ITEM_ID);
    }

    @Test
    @DisplayName("친구 관계가 없으면 공개 도감도 상세 조회할 수 없다")
    void getCollectionItem_nonFriend_rejectsRead() {
        prepareDetail(2L);

        assertThatThrownBy(() -> collectionItemService.getCollectionItem(2L, ITEM_ID))
                .isInstanceOf(CollectionItemAccessDeniedException.class);
    }

    @Test
    @DisplayName("다른 회원은 비공개 도감을 조회할 수 없다")
    void getCollectionItem_privateItem_rejectsOtherMember() {
        collectionItem.update(null, null, false);
        prepareDetail(2L);

        assertThatThrownBy(() -> collectionItemService.getCollectionItem(2L, ITEM_ID))
                .isInstanceOf(CollectionItemAccessDeniedException.class);

        verifyNoInteractions(friendshipRepository);
    }

    @Test
    @DisplayName("교환에 제시한 도감도 수락된 거래에 참여하면 거래 중으로 표시한다")
    void getCollectionItem_acceptedOffer_returnsInProgress() {
        prepareDetail(MEMBER_ID);
        when(collectionTradeRequestRepository.existsByOfferCollectionItem_CollectionItemIdAndStatus(
                ITEM_ID, TradeRequestStatus.ACCEPTED)).thenReturn(true);

        CollectionItemResponse response = collectionItemService.getCollectionItem(MEMBER_ID, ITEM_ID);

        assertThat(response.status()).isEqualTo(CollectionItemStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("도감 수정에서 생략한 필드는 유지한다")
    void updateCollectionItem_missingFields_preservesValues() {
        prepareLockedItem();

        CollectionItemResponse response = collectionItemService.updateCollectionItem(
                MEMBER_ID, ITEM_ID, new CollectionItemUpdateRequest(null, null, null, null));

        assertThat(response.title()).isEqualTo("도감 아이템");
        assertThat(response.description()).isEqualTo("설명");
        assertThat(response.isPublic()).isTrue();
        verify(imageService, never()).uploadInTransaction(any(), any());
    }

    @Test
    @DisplayName("기존 이미지가 없는 도감에 이미지를 추가하면 롤백 정리가 가능한 업로드를 사용한다")
    void updateCollectionItem_firstImage_usesTransactionalUpload() {
        prepareLockedItem();
        MockMultipartFile image = new MockMultipartFile("image", new byte[]{1});
        when(imageService.uploadInTransaction(image, ImageCategory.COLLECTION_ITEM)).thenReturn("new-key");

        collectionItemService.updateCollectionItem(MEMBER_ID, ITEM_ID,
                new CollectionItemUpdateRequest(null, null, null, image));

        assertThat(collectionItem.getImageKey()).isEqualTo("new-key");
        verify(imageService, never()).replace(any(), any(), any());
    }

    @Test
    @DisplayName("다른 회원은 도감을 수정할 수 없다")
    void updateCollectionItem_nonOwner_rejectsRequest() {
        prepareLockedItem();

        assertThatThrownBy(() -> collectionItemService.updateCollectionItem(2L, ITEM_ID,
                new CollectionItemUpdateRequest("변경", null, null, null)))
                .isInstanceOf(CollectionItemAccessDeniedException.class);

        assertThat(collectionItem.getTitle()).isEqualTo("도감 아이템");
        verifyNoInteractions(imageService);
    }

    @Test
    @DisplayName("존재하지 않는 도감을 삭제하면 전용 예외로 처리한다")
    void deleteCollectionItem_missingItem_rejectsRequest() {
        assertThatThrownBy(() -> collectionItemService.deleteCollectionItem(MEMBER_ID, ITEM_ID))
                .isInstanceOf(CollectionItemNotFoundException.class);

        verifyNoInteractions(imageService);
    }

    @ParameterizedTest(name = "삭제를 막는 요청 종류: {0}")
    @EnumSource(BlockingRequest.class)
    @DisplayName("대기 또는 수락된 요청이 있으면 관련 데이터와 이미지를 삭제하지 않는다")
    void deleteCollectionItem_activeRequest_rejectsDeletion(BlockingRequest type) {
        prepareLockedItem();
        List<TradeRequestStatus> tradeStatuses = List.of(TradeRequestStatus.PENDING, TradeRequestStatus.ACCEPTED);
        switch (type) {
            case BEG -> when(begRequestRepository.existsByCollectionItem_CollectionItemIdAndStatusIn(
                    ITEM_ID, List.of(BegRequestStatus.PENDING, BegRequestStatus.ACCEPTED))).thenReturn(true);
            case TARGET -> when(collectionTradeRequestRepository.existsByCollectionItem_CollectionItemIdAndStatusIn(
                    ITEM_ID, tradeStatuses)).thenReturn(true);
            case OFFER -> when(collectionTradeRequestRepository.existsByOfferCollectionItem_CollectionItemIdAndStatusIn(
                    ITEM_ID, tradeStatuses)).thenReturn(true);
            case ITEM -> when(tradeRequestRepository.existsByItem_CollectionItem_CollectionItemIdAndStatusIn(
                    ITEM_ID, tradeStatuses)).thenReturn(true);
        }

        assertThatThrownBy(() -> collectionItemService.deleteCollectionItem(MEMBER_ID, ITEM_ID))
                .isInstanceOfSatisfying(CollectionItemTradeInProgressException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COLLECTION_ITEM_TRADE_IN_PROGRESS));

        verify(collectionItemRepository, never()).delete(any());
        verifyNoInteractions(imageService);
    }

    @Test
    @DisplayName("거절·취소된 요청을 정리한 뒤 도감을 삭제하고 커밋 후 이미지를 정리한다")
    void deleteCollectionItem_withoutActiveRequests_deletesItem() {
        prepareLockedItem();
        collectionItem.updateImageKey("old-key");

        collectionItemService.deleteCollectionItem(MEMBER_ID, ITEM_ID);

        verify(collectionTradeRequestRepository).deleteAllDeletableByCollectionItemId(ITEM_ID,
                List.of(TradeRequestStatus.REJECTED, TradeRequestStatus.CANCELLED),
                TradeRequestStatus.COMPLETED, CollectionTradeType.RENTAL);
        verify(begRequestRepository).deleteAllByCollectionItemIdAndStatusIn(ITEM_ID,
                List.of(BegRequestStatus.REJECTED, BegRequestStatus.CANCELLED));
        verify(collectionItemRepository).delete(collectionItem);
        verify(imageService).deleteAfterCommit("old-key");
    }

    private void prepareDetail(Long requesterId) {
        when(memberRepository.findById(requesterId)).thenReturn(Optional.of(MemberFixture.createMember(requesterId)));
        when(collectionItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(collectionItem));
    }

    private void prepareLockedItem() {
        when(collectionItemRepository.findLockedById(ITEM_ID)).thenReturn(Optional.of(collectionItem));
    }

    private enum BlockingRequest {
        BEG, TARGET, OFFER, ITEM
    }
}
