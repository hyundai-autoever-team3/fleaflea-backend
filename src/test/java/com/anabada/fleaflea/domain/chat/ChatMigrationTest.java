package com.anabada.fleaflea.domain.chat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");
    private static final String INSERT_ROOM = """
            INSERT INTO chat_rooms(member_low_id, member_high_id, updated_at, created_at)
            VALUES (%d, %d, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;
    private static final String INSERT_MESSAGE = """
            INSERT INTO chat_messages(room_id, sender_id, content, client_message_id, created_at)
            VALUES (%d, 1, 'hello', '%s', CURRENT_TIMESTAMP)
            """;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        POSTGRES.start();

        try (Connection connection = openConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V18__create_friend_chat.sql"));
        }
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("constraintViolations")
    @DisplayName("채팅 마이그레이션의 제약 조건을 위반하면 해당 PostgreSQL 오류가 발생한다")
    void migration_rejectsConstraintViolation(String caseName, String invalidSql, String expectedSqlState) throws SQLException {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE TABLE chat_messages, chat_rooms RESTART IDENTITY CASCADE");
            statement.executeUpdate(INSERT_ROOM.formatted(1, 2));
            statement.executeUpdate(INSERT_MESSAGE.formatted(1, "client-1"));

            assertThatThrownBy(() -> statement.executeUpdate(invalidSql))
                    .isInstanceOfSatisfying(SQLException.class,
                            exception -> assertThat(exception.getSQLState()).isEqualTo(expectedSqlState));
        }
    }

    private static Stream<Arguments> constraintViolations() {
        return Stream.of(
                Arguments.of("동일 회원 쌍 중복", INSERT_ROOM.formatted(1, 2), "23505"),
                Arguments.of("회원 쌍 정렬 위반", INSERT_ROOM.formatted(2, 1), "23514"),
                Arguments.of("동일 메시지 ID 중복", INSERT_MESSAGE.formatted(1, "client-1"), "23505"),
                Arguments.of("존재하지 않는 채팅방 참조", INSERT_MESSAGE.formatted(999, "client-2"), "23503")
        );
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
