package com.anabada.fleaflea.domain.item.service;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.dto.ItemDetailResponse;
import com.anabada.fleaflea.domain.item.dto.ItemUpdateRequest;
import com.anabada.fleaflea.domain.item.exception.ItemNotFoundException;
import com.anabada.fleaflea.domain.item.repository.ItemRepository;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.exception.MarketNotParticipantException;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.fixture.ItemFixture;
import com.anabada.fleaflea.fixture.MarketFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemDetailImageUrlTest {

    private static final long ITEM_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long VIEWER_ID = 3L;
    private static final long MARKET_ID = 4L;
    private static final String ORIGINAL_IMAGE_URL = "https://example.test/items/original.png?signature=test";

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private MarketMemberRepository marketMemberRepository;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ItemService itemService;

    private Item item;

    @BeforeEach
    void setUp() {
        Member seller = MemberFixture.createMember(SELLER_ID);
        Market market = MarketFixture.createMarket(seller);
        ReflectionTestUtils.setField(market, "marketId", MARKET_ID);
        item = ItemFixture.createItem(market, seller, "상품");
        ReflectionTestUtils.setField(item, "itemId", ITEM_ID);
    }

    @Test
    @DisplayName("상품 상세는 목록 조회 없이 원본 URL과 기존 이미지 키를 반환한다")
    void findItem_returnsOriginalImageUrl() {
        when(itemRepository.findWithSellerByItemId(ITEM_ID)).thenReturn(Optional.of(item));
        when(marketMemberRepository.existsByMarket_MarketIdAndMember_MemberId(MARKET_ID, VIEWER_ID)).thenReturn(true);
        when(imageService.getUrl(null)).thenReturn(null);
        when(imageService.getUrl(item.getImageKey())).thenReturn(ORIGINAL_IMAGE_URL);

        ItemDetailResponse response = itemService.findItem(ITEM_ID, VIEWER_ID);

        assertThat(response.imageKey()).isEqualTo(item.getImageKey());
        assertThat(response.imageUrl()).isEqualTo(ORIGINAL_IMAGE_URL);
        verify(imageService).getUrl(item.getImageKey());
        verify(imageService, never()).getListUrls(anyCollection());
    }

    @Test
    @DisplayName("상품 수정 응답에도 원본 이미지 URL을 포함한다")
    void updateItem_returnsOriginalImageUrl() {
        when(itemRepository.findWithSellerByItemId(ITEM_ID)).thenReturn(Optional.of(item));
        when(imageService.getUrl(null)).thenReturn(null);
        when(imageService.getUrl(item.getImageKey())).thenReturn(ORIGINAL_IMAGE_URL);
        ItemUpdateRequest itemUpdateRequest = new ItemUpdateRequest("수정 상품", null, null, null, null);

        ItemDetailResponse response = itemService.updateItem(ITEM_ID, SELLER_ID, itemUpdateRequest);

        assertThat(response.title()).isEqualTo("수정 상품");
        assertThat(response.imageUrl()).isEqualTo(ORIGINAL_IMAGE_URL);
        assertThat(response.imageKey()).isEqualTo(item.getImageKey());
        verify(imageService, never()).getListUrls(anyCollection());
    }

    @Test
    @DisplayName("마켓 참여자가 아니면 원본 URL을 생성하지 않는다")
    void findItem_outsiderDoesNotSignImageUrl() {
        when(itemRepository.findWithSellerByItemId(ITEM_ID)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.findItem(ITEM_ID, VIEWER_ID))
                .isInstanceOf(MarketNotParticipantException.class);
        verifyNoInteractions(imageService);
    }

    @Test
    @DisplayName("없는 상품은 이미지 URL 생성 전에 기존 예외를 반환한다")
    void findItem_missingItemDoesNotSignImageUrl() {
        when(itemRepository.findWithSellerByItemId(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.findItem(ITEM_ID, VIEWER_ID))
                .isInstanceOf(ItemNotFoundException.class);
        verifyNoInteractions(imageService);
    }
}
