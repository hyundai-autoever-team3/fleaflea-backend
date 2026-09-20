package com.anabada.fleaflea.domain.item.repository;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

import static com.anabada.fleaflea.domain.item.domain.QItem.item;

@RequiredArgsConstructor
public class ItemRepositoryImpl implements ItemRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Item> searchInMarket(
            Long marketId,
            ItemTradeType tradeType,
            ItemStatus status,
            String keyword,
            Pageable pageable
    ) {
        // 값이 없는 조건은 null이 되어 where에서 빠진다. 필터 없는 목록의 count가
        // market_id 조건만 남아야 인덱스(idx_items_market_created_at)만 읽고 셀 수 있다.
        BooleanExpression[] conditions = {
                item.market.marketId.eq(marketId),
                tradeTypeEq(tradeType),
                statusEq(status),
                titleContains(keyword)
        };

        // 정렬은 idx_items_market_created_at의 컬럼 순서와 같아야 첫 페이지를 20건만 읽는다.
        List<Item> content = queryFactory
                .selectFrom(item)
                .where(conditions)
                .orderBy(item.createdAt.desc(), item.itemId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(item.count())
                .from(item)
                .where(conditions);

        // 첫 페이지 결과가 페이지 크기보다 적거나 마지막 페이지면 count 쿼리를 실행하지 않는다.
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression tradeTypeEq(ItemTradeType tradeType) {
        return tradeType == null ? null : item.tradeType.eq(tradeType);
    }

    private BooleanExpression statusEq(ItemStatus status) {
        return status == null ? null : item.status.eq(status);
    }

    // 기존 쿼리와 같이 DB의 lower()로 대소문자를 무시하고, 와일드카드(%, _)는 이스케이프하지 않는다.
    private BooleanExpression titleContains(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        return item.title.lower().like(Expressions.asString("%" + keyword + "%").lower());
    }
}
