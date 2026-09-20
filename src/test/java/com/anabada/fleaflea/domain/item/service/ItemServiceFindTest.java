package com.anabada.fleaflea.domain.item.service;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import com.anabada.fleaflea.domain.item.dto.ItemDetailResponse;
import com.anabada.fleaflea.domain.item.dto.ItemSummaryResponse;
import com.anabada.fleaflea.domain.item.exception.ItemNotFoundException;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.exception.MarketNotFoundException;
import com.anabada.fleaflea.domain.market.exception.MarketNotParticipantException;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.fixture.ItemFixture;
import com.anabada.fleaflea.fixture.MarketFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.dto.PageResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ItemServiceFindTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Autowired
    private ItemService itemService;

    @Autowired
    private EntityManager em;

    private Member seller;
    private Member viewer;
    private Market market;

    @BeforeEach
    void setUp() {
        seller = persist(MemberFixture.createMember("seller"));
        viewer = persist(MemberFixture.createMember("viewer"));
        market = persist(MarketFixture.createMarket(seller));
        join(market, seller);
        join(market, viewer);
    }

    @Test
    @DisplayName("요청한 마켓의 상품만 조회한다")
    void findItemsOnlyInMarket() {
        Market otherMarket = persist(MarketFixture.createMarket(seller));
        join(otherMarket, seller);
        persistItem(market, "mine", BASE_TIME);
        persistItem(otherMarket, "other", BASE_TIME);

        PageResponse<ItemSummaryResponse> response = findItems(null, null, null, 0, 10);

        assertThat(titles(response)).containsExactly("mine");
    }

    @Test
    @DisplayName("최신 등록순으로 정렬하고, 등록 시각이 같으면 ID 역순으로 정렬한다")
    void findItemsSortedByCreatedAtThenItemId() {
        Item oldest = persistItem(market, "oldest", BASE_TIME.minusHours(2));
        Item sameTimeFirst = persistItem(market, "same-1", BASE_TIME);
        Item sameTimeSecond = persistItem(market, "same-2", BASE_TIME);
        Item newest = persistItem(market, "newest", BASE_TIME.plusHours(1));

        PageResponse<ItemSummaryResponse> response = findItems(null, null, null, 0, 10);

        assertThat(response.content())
                .extracting(ItemSummaryResponse::itemId)
                .containsExactly(
                        newest.getItemId(),
                        sameTimeSecond.getItemId(),
                        sameTimeFirst.getItemId(),
                        oldest.getItemId()
                );
    }

    @Test
    @DisplayName("요청한 페이지의 상품과 전체 개수를 반환한다")
    void findItemsPaginated() {
        for (int i = 0; i < 5; i++) {
            persistItem(market, "item-" + i, BASE_TIME.plusMinutes(i));
        }

        PageResponse<ItemSummaryResponse> response = findItems(null, null, null, 1, 2);

        assertThat(titles(response)).containsExactly("item-2", "item-1");
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.hasPrevious()).isTrue();
    }

    @Test
    @DisplayName("거래 방식으로 필터링한다")
    void findItemsFilteredByTradeType() {
        persistItem(market, "sale", ItemTradeType.SALE, ItemStatus.AVAILABLE, BASE_TIME);
        persistItem(market, "giveaway", ItemTradeType.GIVEAWAY, ItemStatus.AVAILABLE, BASE_TIME.plusMinutes(1));
        persistItem(market, "rental", ItemTradeType.RENTAL, ItemStatus.AVAILABLE, BASE_TIME.plusMinutes(2));

        PageResponse<ItemSummaryResponse> response = findItems(ItemTradeType.GIVEAWAY, null, null, 0, 10);

        assertThat(titles(response)).containsExactly("giveaway");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("상품 상태로 필터링한다")
    void findItemsFilteredByStatus() {
        persistItem(market, "available", ItemTradeType.SALE, ItemStatus.AVAILABLE, BASE_TIME);
        persistItem(market, "in-progress", ItemTradeType.SALE, ItemStatus.IN_PROGRESS, BASE_TIME.plusMinutes(1));
        persistItem(market, "completed", ItemTradeType.SALE, ItemStatus.COMPLETED, BASE_TIME.plusMinutes(2));

        PageResponse<ItemSummaryResponse> response = findItems(null, ItemStatus.COMPLETED, null, 0, 10);

        assertThat(titles(response)).containsExactly("completed");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("상품명에 검색어가 포함된 상품을 대소문자 구분 없이 조회한다")
    void findItemsByKeywordIgnoringCase() {
        persistItem(market, "Apple Magic Keyboard", BASE_TIME);
        persistItem(market, "apple pencil", BASE_TIME.plusMinutes(1));
        persistItem(market, "Samsung Mouse", BASE_TIME.plusMinutes(2));

        assertThat(titles(findItems(null, null, "APPLE", 0, 10)))
                .containsExactly("apple pencil", "Apple Magic Keyboard");
        assertThat(titles(findItems(null, null, "board", 0, 10)))
                .containsExactly("Apple Magic Keyboard");
        assertThat(findItems(null, null, "iphone", 0, 10).totalElements()).isZero();
    }

    @Test
    @DisplayName("검색어가 없으면 상품명이 없는 상품도 목록에 포함하고, 검색어가 있으면 제외한다")
    void findItemsIncludesItemWithoutTitleOnlyWithoutKeyword() {
        // 이전 쿼리는 검색어가 없어도 `lower(title) like '%%'`를 붙여서 상품명이 NULL인 상품을 뺐다.
        persistItem(market, null, BASE_TIME);
        persistItem(market, "titled", BASE_TIME.plusMinutes(1));

        PageResponse<ItemSummaryResponse> withoutKeyword = findItems(null, null, null, 0, 10);
        assertThat(titles(withoutKeyword)).containsExactly("titled", null);
        assertThat(withoutKeyword.totalElements()).isEqualTo(2);

        assertThat(titles(findItems(null, null, "titled", 0, 10))).containsExactly("titled");
    }

    @Test
    @DisplayName("검색어, 거래 방식, 상품 상태를 함께 적용한다")
    void findItemsWithAllConditions() {
        persistItem(market, "camera body", ItemTradeType.SALE, ItemStatus.AVAILABLE, BASE_TIME);
        persistItem(market, "camera lens", ItemTradeType.RENTAL, ItemStatus.AVAILABLE, BASE_TIME.plusMinutes(1));
        persistItem(market, "camera strap", ItemTradeType.SALE, ItemStatus.COMPLETED, BASE_TIME.plusMinutes(2));
        persistItem(market, "tripod", ItemTradeType.SALE, ItemStatus.AVAILABLE, BASE_TIME.plusMinutes(3));

        PageResponse<ItemSummaryResponse> response =
                findItems(ItemTradeType.SALE, ItemStatus.AVAILABLE, "camera", 0, 10);

        assertThat(titles(response)).containsExactly("camera body");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 마켓의 상품 목록을 조회하면 예외가 발생한다")
    void findItemsInMissingMarket() {
        assertThatThrownBy(() -> itemService.findItems(
                Long.MAX_VALUE, viewer.getMemberId(), 0, 10, null, null, null
        )).isInstanceOf(MarketNotFoundException.class);
    }

    @Test
    @DisplayName("마켓 참여자가 아니면 상품 목록을 조회할 수 없다")
    void findItemsByOutsider() {
        Member outsider = persist(MemberFixture.createMember("outsider"));

        assertThatThrownBy(() -> itemService.findItems(
                market.getMarketId(), outsider.getMemberId(), 0, 10, null, null, null
        )).isInstanceOf(MarketNotParticipantException.class);
    }

    @Test
    @DisplayName("상품 상세를 판매자 정보와 함께 조회한다")
    void findItemWithSeller() {
        Item item = persistItem(market, "detail", BASE_TIME);

        ItemDetailResponse response = itemService.findItem(item.getItemId(), viewer.getMemberId());

        assertThat(response.itemId()).isEqualTo(item.getItemId());
        assertThat(response.marketId()).isEqualTo(market.getMarketId());
        assertThat(response.title()).isEqualTo("detail");
        assertThat(response.seller().id()).isEqualTo(seller.getMemberId());
        assertThat(response.seller().nickname()).isEqualTo(seller.getNickname());
    }

    @Test
    @DisplayName("마켓 참여자가 아니면 상품 상세를 조회할 수 없다")
    void findItemByOutsider() {
        Item item = persistItem(market, "detail", BASE_TIME);
        Member outsider = persist(MemberFixture.createMember("outsider"));

        assertThatThrownBy(() -> itemService.findItem(item.getItemId(), outsider.getMemberId()))
                .isInstanceOf(MarketNotParticipantException.class);
    }

    @Test
    @DisplayName("존재하지 않는 상품을 조회하면 예외가 발생한다")
    void findMissingItem() {
        assertThatThrownBy(() -> itemService.findItem(Long.MAX_VALUE, viewer.getMemberId()))
                .isInstanceOf(ItemNotFoundException.class);
    }

    private PageResponse<ItemSummaryResponse> findItems(
            ItemTradeType tradeType,
            ItemStatus status,
            String keyword,
            int page,
            int size
    ) {
        // 등록 시각을 SQL로 바꿨으므로, 1차 캐시의 엔티티가 아니라 DB의 값을 읽도록 비운다.
        em.flush();
        em.clear();

        return itemService.findItems(
                market.getMarketId(),
                viewer.getMemberId(),
                page,
                size,
                tradeType,
                status,
                keyword
        );
    }

    private List<String> titles(PageResponse<ItemSummaryResponse> response) {
        return response.content().stream()
                .map(ItemSummaryResponse::title)
                .toList();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private void join(Market market, Member member) {
        persist(MarketMember.create(market, member));
    }

    private Item persistItem(Market market, String title, LocalDateTime createdAt) {
        return persistItem(market, title, ItemTradeType.SALE, ItemStatus.AVAILABLE, createdAt);
    }

    private Item persistItem(
            Market market,
            String title,
            ItemTradeType tradeType,
            ItemStatus status,
            LocalDateTime createdAt
    ) {
        Item item = persist(ItemFixture.createItem(market, seller, title, tradeType, status));
        // 저장해야 식별자가 생기고, 그 뒤에 등록 시각을 바꿀 수 있다.
        em.flush();
        overrideCreatedAt(item, createdAt);

        return item;
    }

    /**
     * 등록 시각은 JPA Auditing이 저장 시점으로 채우고 `updatable = false`라 JPA로는 바꿀 수 없다.
     * 정렬을 검증하려면 시각을 직접 정해야 하므로 SQL로 바꾼다.
     */
    private void overrideCreatedAt(Item item, LocalDateTime createdAt) {
        em.createNativeQuery("UPDATE items SET created_at = :createdAt WHERE item_id = :itemId")
                .setParameter("createdAt", createdAt)
                .setParameter("itemId", item.getItemId())
                .executeUpdate();
    }
}
