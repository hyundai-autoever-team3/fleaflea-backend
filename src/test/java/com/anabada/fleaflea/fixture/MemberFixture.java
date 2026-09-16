package com.anabada.fleaflea.fixture;

import com.anabada.fleaflea.domain.member.domain.Member;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.util.ReflectionTestUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemberFixture {

    public static Member createMember(Long memberId) {
        Member member = Member.create(
                "test" + memberId + "@example.com",
                "password",
                "member" + memberId
        );

        // 리플렉션으로 테스트용 id 주입
        ReflectionTestUtils.setField(member, "memberId", memberId);

        return member;
    }
}