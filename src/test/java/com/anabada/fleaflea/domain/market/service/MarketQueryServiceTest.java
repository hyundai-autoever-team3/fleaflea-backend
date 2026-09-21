package com.anabada.fleaflea.domain.market.service;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.domain.RelationshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.marketmember.dto.MarketMemberResponse;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.fixture.MarketFixture;
import com.anabada.fleaflea.fixture.MarketMemberFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.dto.PageResponse;
import com.anabada.fleaflea.global.image.ImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketQueryServiceTest {

    private static final Long REQUESTER_ID = 1L;
    private static final Long MARKET_ID = 10L;

    @Mock
    private MarketMemberRepository marketMembers;
    @Mock
    private MemberRepository members;
    @Mock
    private MarketRepository markets;
    @Mock
    private FriendshipRepository friendships;
    @Mock
    private ImageService images;

    @InjectMocks
    private MarketQueryService service;

    @Test
    void returnsRelationshipStatusForEachMarketMember() {
        Member requester = MemberFixture.createMember(REQUESTER_ID);
        Member friend = MemberFixture.createMember(2L);
        Member requested = MemberFixture.createMember(3L);
        Member requestReceived = MemberFixture.createMember(4L);
        Member unrelated = MemberFixture.createMember(5L);
        Market market = MarketFixture.createMarket(requester);

        List<MarketMember> participants = List.of(
                MarketMemberFixture.createMarketMember(market, requester),
                MarketMemberFixture.createMarketMember(market, friend),
                MarketMemberFixture.createMarketMember(market, requested),
                MarketMemberFixture.createMarketMember(market, requestReceived),
                MarketMemberFixture.createMarketMember(market, unrelated)
        );

        Friendship accepted = Friendship.create(
                requester,
                friend,
                FriendshipStatus.PENDING
        );
        accepted.accept();
        Friendship sent = Friendship.create(
                requester,
                requested,
                FriendshipStatus.PENDING
        );
        Friendship received = Friendship.create(
                requestReceived,
                requester,
                FriendshipStatus.PENDING
        );

        PageRequest pageable = PageRequest.of(0, 20);
        when(members.findById(REQUESTER_ID)).thenReturn(Optional.of(requester));
        when(markets.findById(MARKET_ID)).thenReturn(Optional.of(market));
        when(marketMembers.existsByMarketAndMember(market, requester)).thenReturn(true);
        when(marketMembers.findAllByMarket(market, pageable))
                .thenReturn(new PageImpl<>(participants, pageable, participants.size()));
        when(friendships.findActiveRelationships(
                REQUESTER_ID,
                List.of(2L, 3L, 4L, 5L)
        )).thenReturn(List.of(accepted, sent, received));

        PageResponse<MarketMemberResponse> response =
                service.getMarketMembers(REQUESTER_ID, MARKET_ID, pageable);

        Map<Long, MarketMemberResponse> byMemberId = response.content().stream()
                .collect(Collectors.toMap(
                        MarketMemberResponse::memberId,
                        Function.identity()
                ));

        assertThat(byMemberId.get(REQUESTER_ID).relationshipStatus())
                .isEqualTo(RelationshipStatus.SELF);
        assertThat(byMemberId.get(2L).relationshipStatus())
                .isEqualTo(RelationshipStatus.FRIEND);
        assertThat(byMemberId.get(3L).relationshipStatus())
                .isEqualTo(RelationshipStatus.REQUESTED);
        assertThat(byMemberId.get(4L).relationshipStatus())
                .isEqualTo(RelationshipStatus.REQUEST_RECEIVED);
        assertThat(byMemberId.get(5L).relationshipStatus())
                .isEqualTo(RelationshipStatus.NONE);

        verify(friendships).findActiveRelationships(
                REQUESTER_ID,
                List.of(2L, 3L, 4L, 5L)
        );
    }
}
