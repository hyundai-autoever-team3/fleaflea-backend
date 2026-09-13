package com.anabada.fleaflea.domain.refreshtoken.repository;

import com.anabada.fleaflea.domain.refreshtoken.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
}
