package com.anabada.fleaflea.domain.poke.repository;

import com.anabada.fleaflea.domain.poke.domain.MemberPoke;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberPokeRepository extends JpaRepository<MemberPoke, Long> {

    @EntityGraph(attributePaths = "sender")
    Page<MemberPoke> findByRecipient_MemberId(Long recipientId, Pageable pageable);
}
