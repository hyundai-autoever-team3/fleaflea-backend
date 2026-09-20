package com.anabada.fleaflea.domain.collection.repository;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemSearchCondition;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.ArrayList;

import static com.anabada.fleaflea.domain.collection.domain.QCollectionItem.collectionItem;

@Repository
@RequiredArgsConstructor
public class CollectionItemRepositoryImpl implements CollectionItemRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<CollectionItem> search(
            Long ownerId,
            boolean publicOnly,
            CollectionItemSearchCondition condition,
            Pageable pageable
    ) {
        List<CollectionItem> content = queryFactory
                .selectFrom(collectionItem)
                .join(collectionItem.owner).fetchJoin()
                .where(
                        collectionItem.owner.memberId.eq(ownerId),
                        publicOnly(publicOnly),
                        titleContains(condition)
                )
                .orderBy(orderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        var countQuery = queryFactory
                .select(collectionItem.count())
                .from(collectionItem)
                .where(
                        collectionItem.owner.memberId.eq(ownerId),
                        publicOnly(publicOnly),
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

    private BooleanExpression publicOnly(boolean publicOnly) {
        return publicOnly ? collectionItem.isPublic.isTrue() : null;
    }

    private BooleanExpression titleContains(
            CollectionItemSearchCondition condition
    ) {
        if (condition == null
                || condition.title() == null
                || condition.title().isBlank()) {
            return null;
        }

        return collectionItem.title.containsIgnoreCase(
                condition.title().trim()
        );
    }

    private OrderSpecifier<?>[] orderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        for (org.springframework.data.domain.Sort.Order order
                : pageable.getSort()) {
            switch (order.getProperty()) {
                case "title" -> orders.add(new OrderSpecifier<>(
                        direction(order),
                        collectionItem.title
                ));
                case "updatedAt" -> orders.add(new OrderSpecifier<>(
                        direction(order),
                        collectionItem.updatedAt
                ));
                default -> orders.add(new OrderSpecifier<>(
                        direction(order),
                        collectionItem.createdAt
                ));
            }
        }

        if (orders.isEmpty()) {
            return new OrderSpecifier<?>[]{collectionItem.createdAt.desc()};
        }

        return orders.toArray(OrderSpecifier[]::new);
    }

    private Order direction(org.springframework.data.domain.Sort.Order order) {
        return order.isAscending() ? Order.ASC : Order.DESC;
    }
}
