package com.anabada.fleaflea.domain.member.repository;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.domain.OAuth2Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository
        extends JpaRepository<Member, Long> {

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);

    boolean existsByOauth2ProviderAndOauth2Id(
            OAuth2Provider oauth2Provider,
            String oauth2Id
    );

    Optional<Member> findByEmail(String email);

    Optional<Member> findByNickname(String nickname);

    Optional<Member> findByOauth2ProviderAndOauth2Id(
            OAuth2Provider oauth2Provider,
            String oauth2Id
    );
}
