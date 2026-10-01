package com.anabada.fleaflea.domain.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

class ChatMigrationTest {
    @Test
    @DisplayName("채팅 마이그레이션으로 테이블을 생성하고 회원 쌍·메시지 중복과 잘못된 방 참조를 막는다")
    void migrationCreatesTablesAndProtectsUniquePairsAndClientIds() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:16")) {
            postgres.start();
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                ScriptUtils.executeSqlScript(connection,
                        new ClassPathResource("db/migration/V18__create_friend_chat.sql"));
                try (var sql = connection.createStatement()) {
                    String room = "INSERT INTO chat_rooms(member_low_id,member_high_id,updated_at,created_at) VALUES ";
                    sql.executeUpdate(room + "(1,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    assertThatThrownBy(() -> sql.executeUpdate(room + "(1,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"))
                            .isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> sql.executeUpdate(room + "(2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"))
                            .isInstanceOf(SQLException.class);
                    String message = "INSERT INTO chat_messages(room_id,sender_id,content,client_message_id,created_at) VALUES ";
                    sql.executeUpdate(message + "(1,1,'hello','client-1',CURRENT_TIMESTAMP)");
                    assertThatThrownBy(() -> sql.executeUpdate(message + "(1,1,'hello','client-1',CURRENT_TIMESTAMP)"))
                            .isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> sql.executeUpdate(message + "(999,1,'hello','client-2',CURRENT_TIMESTAMP)"))
                            .isInstanceOf(SQLException.class);
                }
            }
        }
    }
}
