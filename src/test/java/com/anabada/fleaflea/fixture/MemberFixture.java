package com.anabada.fleaflea.fixture;

import com.anabada.fleaflea.domain.member.domain.Member;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemberFixture {

    // DB에 저장하는 테스트용. 이메일과 닉네임에 유니크 제약이 있어 매번 다른 값으로 만든다.
    public static Member createMember(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        return Member.create(
                prefix + "-" + suffix + "@example.test",
                "password",
                prefix + "-" + suffix
        );
    }

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