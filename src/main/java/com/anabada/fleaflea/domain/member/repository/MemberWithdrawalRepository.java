package com.anabada.fleaflea.domain.member.repository;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.begrequest.domain.QBegRequest;
import com.anabada.fleaflea.domain.collectionitem.domain.QCollectionItem;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.QItem;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.trade.domain.QCollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.QTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLSubQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.anabada.fleaflea.domain.begrequest.domain.QBegRequest.begRequest;
import static com.anabada.fleaflea.domain.collectionitem.domain.QCollectionItem.collectionItem;
import static com.anabada.fleaflea.domain.friendship.domain.QFriendship.friendship;
import static com.anabada.fleaflea.domain.item.domain.QItem.item;
import static com.anabada.fleaflea.domain.itemcomment.domain.QItemComment.itemComment;
import static com.anabada.fleaflea.domain.market.domain.QMarket.market;
import static com.anabada.fleaflea.domain.marketmember.domain.QMarketMember.marketMember;
import static com.anabada.fleaflea.domain.member.domain.QMember.member;
import static com.anabada.fleaflea.domain.notification.domain.QNotification.notification;
import static com.anabada.fleaflea.domain.poke.domain.QMemberPoke.memberPoke;
import static com.anabada.fleaflea.domain.trade.domain.QCollectionTradeRequest.collectionTradeRequest;
import static com.anabada.fleaflea.domain.trade.domain.QTrade.trade;
import static com.anabada.fleaflea.domain.trade.domain.QTradeRequest.tradeRequest;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MemberWithdrawalRepository {
    private final JPAQueryFactory queryFactory;

    /*
     * 1. 삭제 대상 잠금
     *
     * 현재 존재하는 대상에 대한 변경 직렬화
     * 새로운 참조 삽입은 DB FK와 서비스의 트랜잭션 정책으로 보호
     * */
    public void lockTargets(Long memberId) {
        queryFactory
                .selectFrom(collectionItem)
                .where(collectionItem.owner.memberId.eq(memberId))
                .orderBy(collectionItem.collectionItemId.asc())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetch();

        queryFactory
                .selectFrom(item)
                .where(item.seller.memberId.eq(memberId))
                .orderBy(item.itemId.asc())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetch();
        queryFactory
                .selectFrom(tradeRequest)
                .where(relatedItemRequest(tradeRequest, memberId))
                .orderBy(tradeRequest.tradeRequestId.asc())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetch();
        queryFactory
                .selectFrom(collectionTradeRequest)
                .where(relatedCollectionRequest(
                        collectionTradeRequest,
                        memberId
                ))
                .orderBy(collectionTradeRequest.collectionTradeRequestId.asc())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetch();
        queryFactory
                .selectFrom(begRequest)
                .where(relatedBegRequest(
                        begRequest,
                        memberId
                ))
                .orderBy(begRequest.begRequestId.asc())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetch();
    }

    /*
     * 2. 탈퇴 조건 조회
     */
    public boolean hasOngoingTrade(Long memberId) {
        boolean ongoingItem = queryFactory
                .selectOne()
                .from(item)
                .where(
                        item.seller.memberId.eq(memberId),
                        item.status.eq(ItemStatus.IN_PROGRESS)
                )
                .fetchFirst() != null;

        boolean acceptedItemRequest = queryFactory
                .selectOne()
                .from(tradeRequest)
                .where(
                        relatedItemRequest(tradeRequest, memberId),
                        tradeRequest.status.eq(TradeRequestStatus.ACCEPTED)
                )
                .fetchFirst() != null;

        boolean acceptedCollectionRequest = queryFactory
                .selectOne()
                .from(collectionTradeRequest)
                .where(
                        relatedCollectionRequest(collectionTradeRequest, memberId),
                        collectionTradeRequest.status.eq(TradeRequestStatus.ACCEPTED)
                )
                .fetchFirst() != null;

        boolean acceptedBegRequest = queryFactory
                .selectOne()
                .from(begRequest)
                .where(
                        relatedBegRequest(begRequest,memberId),
                        begRequest.status.eq(BegRequestStatus.ACCEPTED)
                )
                .fetchFirst() != null;

        return ongoingItem
                || acceptedItemRequest
                || acceptedCollectionRequest
                || acceptedBegRequest;
    }

    public boolean ownsMarket(Long memberId) {
        return queryFactory
                .selectOne()
                .from(market)
                .where(market.host.memberId.eq(memberId))
                .fetchFirst() != null;
    }

    /*
    * 3. 이미지 키 조회
    * DB 행을 삭제하기 전에 키 확보
    */
    public List<String> findImageKeys(Long memberId) {
        Set<String> imageKeys = new LinkedHashSet<>();

        imageKeys.add(
                queryFactory
                        .select(member.profileImageKey)
                        .from(member)
                        .where(member.memberId.eq(memberId))
                        .fetchOne()
        );

        imageKeys.addAll(
                queryFactory
                        .select(item.imageKey)
                        .from(item)
                        .where(item.seller.memberId.eq(memberId))
                        .fetch()
        );

        imageKeys.addAll(
                queryFactory
                        .select(collectionItem.imageKey)
                        .from(collectionItem)
                        .where(collectionItem.owner.memberId.eq(memberId))
                        .fetch()
        );

        return imageKeys.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean isImageReferenced(String imageKey) {
        if (imageKey == null) {
            return false;
        }

        return queryFactory
                .selectOne()
                .from(member)
                .where(member.profileImageKey.eq(imageKey))
                .fetchFirst() != null

                || queryFactory
                .selectOne()
                .from(item)
                .where(item.imageKey.eq(imageKey))
                .fetchFirst() != null

                || queryFactory
                .selectOne()
                .from(collectionItem)
                .where(collectionItem.imageKey.eq(imageKey))
                .fetchFirst() != null

                || queryFactory
                .selectOne()
                .from(market)
                .where(market.coverImageKey.eq(imageKey))
                .fetchFirst() != null;
    }

    /*
     * 4. 삭제
     *
     * 아래 메서드는 서비스가 정한 순서대로 호출
     * */
    public long deleteNotifications(Long memberId) {
        BooleanExpression target =
                notification.receiver.memberId.eq(memberId)
                        .or(referenceTo(
                                NotificationReferenceType.ITEM_TRADE_REQUEST,
                                itemRequestIds(memberId)
                        ))
                        .or(referenceTo(
                                NotificationReferenceType.COLLECTION_TRADE_REQUEST,
                                collectionRequestIds(memberId)
                        ))
                        .or(referenceTo(
                                NotificationReferenceType.BEG_REQUEST,
                                begRequestIds(memberId)
                        ))
                        .or(referenceTo(
                                NotificationReferenceType.FRIENDSHIP,
                                friendshipIds(memberId)
                        ))
                        .or(referenceTo(
                                NotificationReferenceType.MEMBER_POKE,
                                pokeIds(memberId)
                        ));
        return queryFactory
                .delete(notification)
                .where(target)
                .execute();
    }

    public void deleteRequests(Long memberId) {
        queryFactory
                .delete(tradeRequest)
                .where(relatedItemRequest(tradeRequest,memberId))
                .execute();
        queryFactory
                .delete(collectionTradeRequest)
                .where(relatedCollectionRequest(collectionTradeRequest,memberId))
                .execute();
        queryFactory
                .delete(begRequest)
                .where(relatedBegRequest(begRequest,memberId))
                .execute();
    }

    public long deleteTrades(Long memberId) {
        return queryFactory
                .delete(trade)
                .where(trade.buyerId.eq(memberId)
                        .or(trade.sellerId.eq(memberId))
                        .or(trade.itemId.in(ownedItemIds(memberId)))
                        .or(trade.tradeRequestId.in(
                                itemRequestIds(memberId)
                        ))
                        .or(trade.collectionTradeRequestId.in(
                                collectionRequestIds(memberId)
                        ))
                        .or(trade.begRequestId.in(
                                begRequestIds(memberId)
                        ))
                )
                .execute();
    }

    public void deleteContents(Long memberId) {
        queryFactory
                .delete(itemComment)
                .where(
                        itemComment.authorId.eq(memberId)
                                .or(itemComment.itemId.in(
                                        ownedItemIds(memberId)
                                ))
                )
                .execute();
        queryFactory
                .delete(item)
                .where(item.seller.memberId.eq(memberId))
                .execute();
        queryFactory
                .delete(collectionItem)
                .where(collectionItem.owner.memberId.eq(memberId))
                .execute();
    }

    public void deleteRelations(Long memberId) {
        queryFactory
                .delete(friendship)
                .where(
                        friendship.requester.memberId.eq(memberId)
                                .or(
                                        friendship.addressee.memberId.eq(
                                                memberId
                                        )
                                )
                )
                .execute();
        queryFactory
                .delete(memberPoke)
                .where(
                        memberPoke.sender.memberId.eq(memberId)
                                .or(
                                        memberPoke.recipient.memberId
                                                .eq(memberId)
                                )
                )
                .execute();
        queryFactory
                .delete(marketMember)
                .where(marketMember.member.memberId.eq(memberId))
                .execute();
    }

    /*
     * 5.재사용할 조건과 서브 쿼리
     */
    private BooleanExpression relatedItemRequest(
            QTradeRequest request,
            Long memberId
    ) {
        return request.requester.memberId.eq(memberId)
                .or(
                        request.item.itemId.in(
                                ownedItemIds(memberId)
                        )
                );
    }

    private BooleanExpression relatedCollectionRequest(
            QCollectionTradeRequest request,
            Long memberId
    ) {
        return request.requester.memberId.eq(memberId)
                .or(request.owner.memberId.eq(memberId))
                .or(
                        request.collectionItem.collectionItemId.in(
                                ownedCollectionItemIds(memberId)
                        )
                )
                .or(
                        request.offerCollectionItem.collectionItemId.in(
                                ownedCollectionItemIds(memberId)
                        )
                );
    }

    private BooleanExpression relatedBegRequest(
            QBegRequest request,
            Long memberId
    ) {
        return request.applicant.memberId.eq(memberId)
                .or(request.owner.memberId.eq(memberId))
                .or(
                        request.collectionItem.collectionItemId.in(
                                ownedCollectionItemIds(memberId)
                        )
                );
    }

    private JPQLSubQuery<Long> ownedItemIds(Long memberId) {
        QItem ownedItem = new QItem("withdrawalOwnedItem");

        return JPAExpressions
                .select(ownedItem.itemId)
                .from(ownedItem)
                .where(ownedItem.seller.memberId.eq(memberId));
    }

    private JPQLSubQuery<Long> ownedCollectionItemIds(Long memberId) {
        QCollectionItem ownedCollection = new QCollectionItem("withdrawalOwnedCollection");

        return JPAExpressions
                .select(ownedCollection.collectionItemId)
                .from(ownedCollection)
                .where(ownedCollection.owner.memberId.eq(memberId));
    }

    private JPQLSubQuery<Long> itemRequestIds(Long memberId) {
        QTradeRequest request =
                new QTradeRequest("withdrawalItemRequest");

        return JPAExpressions
                .select(request.tradeRequestId)
                .from(request)
                .where(relatedItemRequest(request, memberId));
    }

    private JPQLSubQuery<Long> collectionRequestIds(Long memberId) {
        QCollectionTradeRequest request =
                new QCollectionTradeRequest("withdrawalCollectionRequest");

        return JPAExpressions
                .select(request.collectionTradeRequestId)
                .from(request)
                .where(relatedCollectionRequest(request, memberId));
    }

    private JPQLSubQuery<Long> begRequestIds(Long memberId) {
        QBegRequest request =
                new QBegRequest("withdrawalBegRequest");

        return JPAExpressions
                .select(request.begRequestId)
                .from(request)
                .where(relatedBegRequest(request, memberId));
    }

    private JPQLSubQuery<Long> friendshipIds(Long memberId) {
        return JPAExpressions
                .select(friendship.friendshipId)
                .from(friendship)
                .where(friendship.requester.memberId.eq(memberId)
                        .or(
                                friendship.addressee.memberId
                                        .eq(memberId)
                        )
                );
    }

    private JPQLSubQuery<Long> pokeIds(Long memberId) {
        return JPAExpressions
                .select(memberPoke.pokeId)
                .from(memberPoke)
                .where(
                        memberPoke.sender.memberId.eq(memberId)
                                .or(
                                        memberPoke.recipient.memberId
                                                .eq(memberId)
                                )
                );
    }

    private BooleanExpression referenceTo(
            NotificationReferenceType referenceType,
            JPQLSubQuery<Long> referenceIds
    ) {
        return notification.referenceType.eq(referenceType)
                .and(notification.referenceId.in(referenceIds));
    }

}


