package com.anabada.fleaflea.domain.marketmember.repository;

import com.anabada.fleaflea.domain.market.dto.MarketSearchCondition;
import com.anabada.fleaflea.domain.market.dto.MarketSummaryProjection;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.ArrayList;

import static com.anabada.fleaflea.domain.market.domain.QMarket.market;
import static com.anabada.fleaflea.domain.marketmember.domain.QMarketMember.marketMember;
import static com.anabada.fleaflea.domain.member.domain.QMember.member;

@Repository
@RequiredArgsConstructor
public class MarketMemberRepositoryImpl implements MarketMemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<MarketSummaryProjection> searchJoinedMarkets(
            Long memberId,
            MarketSearchCondition condition,
            Pageable pageable
    ) {
        List<MarketSummaryProjection> content = queryFactory
                .select(Projections.constructor(
                        MarketSummaryProjection.class,
                        market.marketId,
                        member.memberId,
                        member.nickname,
                        market.title,
                        market.description,
                        market.coverImageKey,
                        marketMember.joinedAt
                ))
                .from(marketMember)
                .join(marketMember.market, market)
                .join(market.host, member)
                .where(
                        marketMember.member.memberId.eq(memberId),
                        titleContains(condition)
                )
                .orderBy(orderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        var countQuery = queryFactory
                .select(marketMember.count())
                .from(marketMember)
                .join(marketMember.market, market)
                .where(
                        marketMember.member.memberId.eq(memberId),
                        titleContains(condition)
                );

        return PageableExecutionUtils.getPage(
                content,
                pageable,
                () -> {
                    Long total = countQuery.fetchOne();
                    return total == null ? 0 : total;
                }
        );
    }

    private BooleanExpression titleContains(MarketSearchCondition condition) {
        if (condition == null
                || condition.title() == null
                || condition.title().isBlank()) {
            return null;
        }

        return market.title.containsIgnoreCase(condition.title().trim());
    }

    private OrderSpecifier<?>[] orderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        for (org.springframework.data.domain.Sort.Order order
                : pageable.getSort()) {
            switch (order.getProperty()) {
                case "title" -> orders.add(new OrderSpecifier<>(
                        direction(order),
                        market.title
                ));
                default -> orders.add(new OrderSpecifier<>(
                        direction(order),
                        marketMember.joinedAt
                ));
            }
        }

        if (orders.isEmpty()) {
            return new OrderSpecifier<?>[]{marketMember.joinedAt.desc()};
        }

        return orders.toArray(OrderSpecifier[]::new);
    }

    private Order direction(org.springframework.data.domain.Sort.Order order) {
        return order.isAscending() ? Order.ASC : Order.DESC;
    }
}
