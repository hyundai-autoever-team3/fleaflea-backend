package com.anabada.fleaflea.domain.friendship.repository;

import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
@Transactional
class FriendshipMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberRepository memberRepository;

    private Long firstMemberId;
    private Long secondMemberId;

    @BeforeEach
    void setUpExistingFriendship() {
        firstMemberId = memberRepository.save(MemberFixture.createMember("first")).getMemberId();
        secondMemberId = memberRepository.save(MemberFixture.createMember("second")).getMemberId();
        jdbcTemplate.update("INSERT INTO friendships(requester_id, addressee_id, status) VALUES (?, ?, 'PENDING')",
                firstMemberId, secondMemberId);
    }

    @ParameterizedTest(name = "중복 요청 상태={0}")
    @EnumSource(value = FriendshipStatus.class, names = {"PENDING", "ACCEPTED"})
    @DisplayName("반대 방향이라도 활성 친구 관계가 이미 있으면 PostgreSQL 유니크 제약이 거절한다")
    void migration_rejectsReversedActivePair(FriendshipStatus status) throws SQLException {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO friendships(requester_id, addressee_id, status) VALUES (%d, %d, '%s')".formatted(secondMemberId, firstMemberId, status.name())))
                    .isInstanceOfSatisfying(SQLException.class,
                            exception -> assertThat(exception.getSQLState()).isEqualTo("23505"));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Test
    @DisplayName("종료된 친구 요청 이력을 남긴 상태에서 같은 회원 쌍으로 다시 요청할 수 있다")
    void migration_allowsNewRequestAfterRejection() {
        jdbcTemplate.update("UPDATE friendships SET status = 'REJECTED' WHERE requester_id = ? AND addressee_id = ?", firstMemberId, secondMemberId);

        jdbcTemplate.update("INSERT INTO friendships(requester_id, addressee_id, status) VALUES (?, ?, 'PENDING')", secondMemberId, firstMemberId);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM friendships WHERE status = 'PENDING' AND requester_id = ?", Long.class, secondMemberId))
                .isEqualTo(1);
    }
}
