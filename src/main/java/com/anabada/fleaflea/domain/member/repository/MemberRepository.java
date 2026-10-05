package com.anabada.fleaflea.domain.member.repository;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.domain.SocialProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.memberId = :id")
    Optional<Member> findLockedById(@Param("id") Long id);

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);

    boolean existsByOauth2ProviderAndOauth2Id(
            SocialProvider oauth2Provider,
            String oauth2Id
    );

    Optional<Member> findByEmail(String email);

    Optional<Member> findByNickname(String nickname);

    Optional<Member> findByOauth2ProviderAndOauth2Id(
            SocialProvider oauth2Provider,
            String oauth2Id
    );
}
