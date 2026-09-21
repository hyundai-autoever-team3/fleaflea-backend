package com.anabada.fleaflea.domain.query;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.collection.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemSearchCondition;
import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.dto.MarketSearchCondition;
import com.anabada.fleaflea.domain.market.dto.MarketSummaryProjection;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.global.config.JpaAuditingConfig;
import com.anabada.fleaflea.global.config.QueryDslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({QueryDslConfig.class, JpaAuditingConfig.class})
class CollectionMarketQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CollectionItemRepository collectionItemRepository;

    @Autowired
    private MarketMemberRepository marketMemberRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Test
    void collectionSearchAppliesTitlePublicAndPageConditions() {
        Member owner = persistMember("owner@example.com", "owner");
        persistCollectionItem(owner, "공개 텀블러", true);
        persistCollectionItem(owner, "비공개 텀블러", false);
        persistCollectionItem(owner, "공개 가방", true);
        entityManager.flush();
        entityManager.clear();

        Page<CollectionItem> result = collectionItemRepository.search(
                owner.getMemberId(),
                true,
                new CollectionItemSearchCondition("텀블러"),
                PageRequest.of(0, 1, Sort.by("title").ascending())
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(CollectionItem::getTitle)
                .containsExactly("공개 텀블러");
        assertThat(result.getContent().getFirst().getOwner().getNickname())
                .isEqualTo("owner");
    }

    @Test
    void marketSearchReturnsJoinedMarketsMatchingTitle() {
        Member member = persistMember("member@example.com", "member");
        Market neighborhood = persistMarket(member, "동네 장터", "invite-1");
        Market book = persistMarket(member, "독서 모임", "invite-2");
        entityManager.persist(MarketMember.create(neighborhood, member));
        entityManager.persist(MarketMember.create(book, member));
        entityManager.flush();
        entityManager.clear();

        Page<MarketSummaryProjection> result =
                marketMemberRepository.searchJoinedMarkets(
                        member.getMemberId(),
                        new MarketSearchCondition("장터"),
                        PageRequest.of(
                                0,
                                20,
                                Sort.by("joinedAt").descending()
                        )
                );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(MarketSummaryProjection::title)
                .containsExactly("동네 장터");
        assertThat(result.getContent().getFirst().hostNickname())
                .isEqualTo("member");
    }

    @Test
    void activeRelationshipSearchIncludesBothDirectionsAndExcludesFinishedRequests() {
        Member requester = persistMember("requester@example.com", "requester");
        Member friend = persistMember("friend@example.com", "friend");
        Member receivedFrom = persistMember("received@example.com", "received");
        Member rejected = persistMember("rejected@example.com", "rejected");
        Member outsidePage = persistMember("outside@example.com", "outside");

        Friendship accepted = Friendship.create(
                requester,
                friend,
                FriendshipStatus.PENDING
        );
        accepted.accept();
        entityManager.persist(accepted);
        entityManager.persist(Friendship.create(
                receivedFrom,
                requester,
                FriendshipStatus.PENDING
        ));

        Friendship rejectedRequest = Friendship.create(
                requester,
                rejected,
                FriendshipStatus.PENDING
        );
        rejectedRequest.reject();
        entityManager.persist(rejectedRequest);
        entityManager.persist(Friendship.create(
                requester,
                outsidePage,
                FriendshipStatus.PENDING
        ));
        entityManager.flush();
        entityManager.clear();

        List<Friendship> result = friendshipRepository.findActiveRelationships(
                requester.getMemberId(),
                List.of(
                        friend.getMemberId(),
                        receivedFrom.getMemberId(),
                        rejected.getMemberId()
                )
        );

        assertThat(result)
                .extracting(Friendship::getStatus)
                .containsExactlyInAnyOrder(
                        FriendshipStatus.ACCEPTED,
                        FriendshipStatus.PENDING
                );
        assertThat(result)
                .allSatisfy(friendship -> assertThat(friendship.isParticipant(
                        requester.getMemberId()
                )).isTrue());
    }

    private Member persistMember(String email, String nickname) {
        return entityManager.persist(Member.create(email, "password", nickname));
    }

    private void persistCollectionItem(
            Member owner,
            String title,
            boolean isPublic
    ) {
        entityManager.persist(CollectionItem.create(
                owner,
                title,
                "description",
                null,
                isPublic
        ));
    }

    private Market persistMarket(
            Member host,
            String title,
            String inviteCode
    ) {
        return entityManager.persist(Market.create(
                host,
                title,
                "description",
                null,
                inviteCode
        ));
    }
}
