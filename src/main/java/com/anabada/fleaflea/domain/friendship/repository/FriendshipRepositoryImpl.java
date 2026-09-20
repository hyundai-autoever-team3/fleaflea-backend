package com.anabada.fleaflea.domain.friendship.repository;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.member.domain.QMember;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.anabada.fleaflea.domain.friendship.domain.QFriendship.friendship;

@Repository
@RequiredArgsConstructor
public class FriendshipRepositoryImpl implements FriendshipRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<Friendship> findReceivedRequests(Long memberId) {
        QMember requester = new QMember("requester");

        return queryFactory
                .selectFrom(friendship)
                .join(friendship.requester, requester).fetchJoin()
                .where(
                        friendship.addressee.memberId.eq(memberId),
                        friendship.status.eq(FriendshipStatus.PENDING)
                )
                .fetch();
    }

    @Override
    public List<Friendship> findSentRequests(Long memberId) {
        QMember addressee = new QMember("addressee");

        return queryFactory
                .selectFrom(friendship)
                .join(friendship.addressee, addressee).fetchJoin()
                .where(
                        friendship.requester.memberId.eq(memberId),
                        friendship.status.eq(FriendshipStatus.PENDING)
                )
                .fetch();
    }

    @Override
    public List<Friendship> findFriends(Long memberId) {
        QMember requester = new QMember("requester");
        QMember addressee = new QMember("addressee");

        return queryFactory
                .selectFrom(friendship)
                .join(friendship.requester, requester).fetchJoin()
                .join(friendship.addressee, addressee).fetchJoin()
                .where(
                        friendship.status.eq(FriendshipStatus.ACCEPTED),
                        friendship.requester.memberId.eq(memberId)
                                .or(friendship.addressee.memberId.eq(memberId))
                )
                .fetch();
    }
}
