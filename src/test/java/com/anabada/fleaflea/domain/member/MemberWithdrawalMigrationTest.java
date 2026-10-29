package com.anabada.fleaflea.domain.member;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberWithdrawalMigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    @DisplayName("V23의 고아 참조를 보존하며 V24를 적용하고 신규 고아 참조는 차단한다")
    void migration_preservesLegacyOrphansAndRejectsNewOnes() throws SQLException {
        flyway("23").migrate();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO members(member_id, email, password, nickname)
                    VALUES (1, 'first@example.test', 'password', 'first'),
                           (2, 'second@example.test', 'password', 'second')
                    """);
            // 기존 반납 완료 소장품 삭제는 연결된 거래를 남길 수 있었다.
            statement.executeUpdate("""
                    INSERT INTO trades(collection_trade_request_id, buyer_id, seller_id, created_at)
                    VALUES (999, 1, 2, CURRENT_TIMESTAMP)
                    """);
            // 기존 회원 탈퇴는 친구 관계를 정리하지 않았다.
            statement.executeUpdate("""
                    INSERT INTO friendships(requester_id, addressee_id, status)
                    VALUES (1, 999, 'PENDING')
                    """);
        }

        assertThat(flyway("24").migrate().migrationsExecuted).isEqualTo(1);

        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM trades WHERE collection_trade_request_id = 999")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM friendships WHERE addressee_id = 999")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) FROM pg_constraint
                    WHERE contype = 'f' AND NOT convalidated
                      AND connamespace = 'public'::regnamespace
                    """)) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(11);
            }

            assertForeignKeyViolation(statement, """
                    INSERT INTO trades(collection_trade_request_id, buyer_id, seller_id, created_at)
                    VALUES (999, 1, 2, CURRENT_TIMESTAMP)
                    """);
            assertForeignKeyViolation(statement, """
                    INSERT INTO friendships(requester_id, addressee_id, status)
                    VALUES (2, 998, 'PENDING')
                    """);
            assertForeignKeyViolation(statement, """
                    ALTER TABLE trades VALIDATE CONSTRAINT fk_trades_collection_trade_request
                    """);

            // 데이터 정리 후에는 동일한 제약을 완전히 검증할 수 있다.
            statement.executeUpdate("DELETE FROM trades WHERE collection_trade_request_id = 999");
            statement.executeUpdate("ALTER TABLE trades VALIDATE CONSTRAINT fk_trades_collection_trade_request");
            try (var rows = statement.executeQuery("""
                    SELECT convalidated FROM pg_constraint
                    WHERE conname = 'fk_trades_collection_trade_request'
                    """)) {
                rows.next();
                assertThat(rows.getBoolean(1)).isTrue();
            }
        }
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void assertForeignKeyViolation(Statement statement, String sql) {
        assertThatThrownBy(() -> statement.execute(sql))
                .isInstanceOfSatisfying(SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo("23503"));
    }
}
