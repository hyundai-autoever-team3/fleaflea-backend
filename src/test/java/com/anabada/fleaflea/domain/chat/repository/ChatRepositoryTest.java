package com.anabada.fleaflea.domain.chat.repository;

import com.anabada.fleaflea.domain.chat.domain.ChatMessage;
import com.anabada.fleaflea.domain.chat.domain.ChatRoom;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.fixture.ChatFixture;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.config.JpaAuditingConfig;
import com.anabada.fleaflea.global.config.QueryDslConfig;
import com.anabada.fleaflea.support.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, PostgresTestContainerConfiguration.class})
class ChatRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private Member sender;
    private Member receiver;

    @BeforeEach
    void setUp() {
        sender = entityManager.persist(MemberFixture.createMember("sender"));
        receiver = entityManager.persist(MemberFixture.createMember("receiver"));
    }

    @Test
    @DisplayName("읽음 상태를 수정해도 마지막 메시지 기준의 채팅방 순서는 유지된다")
    void findChatRoomsByMemberId_preservesOrderAfterRead() {
        Member member = entityManager.persist(MemberFixture.createMember("member"));
        Member firstFriend = entityManager.persist(MemberFixture.createMember("first"));
        Member secondFriend = entityManager.persist(MemberFixture.createMember("second"));
        ChatRoom olderRoom = entityManager.persist(ChatFixture.createChatRoom(member.getMemberId(), firstFriend.getMemberId()));
        ChatRoom newerRoom = entityManager.persist(ChatFixture.createChatRoom(member.getMemberId(), secondFriend.getMemberId()));
        ChatMessage olderMessage = entityManager.persist(
                ChatFixture.createChatMessage(olderRoom.getId(), firstFriend.getMemberId(), "이전 메시지")
        );
        olderRoom.recordMessage(olderMessage);
        entityManager.flush();

        ChatMessage newerMessage = entityManager.persist(
                ChatFixture.createChatMessage(newerRoom.getId(), secondFriend.getMemberId(), "최근 메시지")
        );
        newerRoom.recordMessage(newerMessage);
        entityManager.flush();
        olderRoom.markMessagesAsRead(member.getMemberId(), olderMessage.getId());
        entityManager.flush();
        entityManager.clear();

        List<ChatRoom> result = chatRoomRepository
                .findChatRoomsByMemberId(member.getMemberId(), PageRequest.of(0, 20))
                .getContent();

        assertThat(result).extracting(ChatRoom::getId).containsExactly(newerRoom.getId(), olderRoom.getId());
        assertThat(result.getLast().getLastReadMessageId(member.getMemberId())).isEqualTo(olderMessage.getId());
    }

    @Test
    @DisplayName("메시지가 없는 방은 생성 시각으로 정렬하고 다른 회원의 방은 제외한다")
    void findChatRoomsByMemberId_ordersEmptyRoomsAndFiltersMember() {
        Member member = entityManager.persist(MemberFixture.createMember("member"));
        Member firstFriend = entityManager.persist(MemberFixture.createMember("first"));
        Member secondFriend = entityManager.persist(MemberFixture.createMember("second"));
        ChatRoom firstRoom = entityManager.persist(ChatFixture.createChatRoom(member.getMemberId(), firstFriend.getMemberId()));
        ChatRoom secondRoom = entityManager.persist(ChatFixture.createChatRoom(member.getMemberId(), secondFriend.getMemberId()));
        entityManager.persist(ChatFixture.createChatRoom(firstFriend.getMemberId(), secondFriend.getMemberId()));
        entityManager.flush();
        entityManager.clear();

        List<ChatRoom> result = chatRoomRepository
                .findChatRoomsByMemberId(member.getMemberId(), PageRequest.of(0, 20))
                .getContent();

        assertThat(result).extracting(ChatRoom::getId).containsExactly(secondRoom.getId(), firstRoom.getId());
    }

    @Test
    @DisplayName("감사 기능으로 생성한 메시지 시각은 PostgreSQL 재조회 후에도 동일하다")
    void save_preservesAuditedTimestampAfterReload() {
        ChatRoom chatRoom = entityManager.persist(ChatFixture.createChatRoom(sender.getMemberId(), receiver.getMemberId()));
        ChatMessage chatMessage = chatMessageRepository.save(
                ChatFixture.createChatMessage(chatRoom.getId(), sender.getMemberId(), "정밀도 검증")
        );
        entityManager.flush();
        entityManager.clear();

        ChatMessage reloadedMessage = chatMessageRepository.findById(chatMessage.getId()).orElseThrow();

        assertThat(chatMessage.getCreatedAt()).isNotNull();
        assertThat(reloadedMessage.getCreatedAt()).isEqualTo(chatMessage.getCreatedAt());
    }

    @Test
    @DisplayName("읽지 않은 개수는 읽음 위치 이후에 상대가 보낸 메시지만 집계한다")
    void countUnreadMessagesByRoomIds_excludesOwnAndReadMessages() {
        ChatRoom chatRoom = entityManager.persist(ChatFixture.createChatRoom(sender.getMemberId(), receiver.getMemberId()));
        ChatMessage firstMessage = entityManager.persist(ChatFixture.createChatMessage(chatRoom.getId(), receiver.getMemberId(), "첫 메시지"));
        entityManager.persist(ChatFixture.createChatMessage(chatRoom.getId(), sender.getMemberId(), "내 메시지"));
        entityManager.persist(ChatFixture.createChatMessage(chatRoom.getId(), receiver.getMemberId(), "마지막 메시지"));
        chatRoom.markMessagesAsRead(sender.getMemberId(), firstMessage.getId());
        entityManager.flush();
        entityManager.clear();

        List<ChatMessageRepository.UnreadCount> counts = chatMessageRepository
                .countUnreadMessagesByRoomIds(List.of(chatRoom.getId()), sender.getMemberId());
        assertThat(counts).singleElement().satisfies(count -> assertThat(count.getUnreadCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("이전 커서의 메시지는 제외하고 그보다 오래된 메시지를 내림차순으로 조회한다")
    void findMessagesBeforeId_excludesCursorAndOrdersDescending() {
        ChatRoom room = entityManager.persist(ChatFixture.createChatRoom(sender.getMemberId(), receiver.getMemberId()));
        ChatMessage first = entityManager.persist(ChatFixture.createChatMessage(room.getId(), receiver.getMemberId(), "첫 메시지"));
        ChatMessage second = entityManager.persist(ChatFixture.createChatMessage(room.getId(), sender.getMemberId(), "두 번째 메시지"));
        ChatMessage cursor = entityManager.persist(ChatFixture.createChatMessage(room.getId(), receiver.getMemberId(), "커서 메시지"));
        entityManager.flush();
        entityManager.clear();

        List<ChatMessage> messages = chatMessageRepository.findMessagesBeforeId(room.getId(), cursor.getId(), PageRequest.of(0, 20));

        assertThat(messages).extracting(ChatMessage::getId).containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("복구 커서의 메시지는 제외하고 이후 메시지를 오름차순으로 조회한다")
    void findMessagesAfterId_excludesCursorAndOrdersAscending() {
        ChatRoom room = entityManager.persist(ChatFixture.createChatRoom(sender.getMemberId(), receiver.getMemberId()));
        ChatMessage cursor = entityManager.persist(ChatFixture.createChatMessage(room.getId(), receiver.getMemberId(), "커서 메시지"));
        ChatMessage second = entityManager.persist(ChatFixture.createChatMessage(room.getId(), sender.getMemberId(), "두 번째 메시지"));
        ChatMessage third = entityManager.persist(ChatFixture.createChatMessage(room.getId(), receiver.getMemberId(), "마지막 메시지"));
        entityManager.flush();
        entityManager.clear();

        List<ChatMessage> messages = chatMessageRepository.findMessagesAfterId(room.getId(), cursor.getId(), PageRequest.of(0, 20));

        assertThat(messages).extracting(ChatMessage::getId).containsExactly(second.getId(), third.getId());
    }
}
