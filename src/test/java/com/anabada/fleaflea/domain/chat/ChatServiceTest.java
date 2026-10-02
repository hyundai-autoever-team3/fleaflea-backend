package com.anabada.fleaflea.domain.chat;

import com.anabada.fleaflea.domain.chat.dto.ChatDtos.*;
import com.anabada.fleaflea.domain.chat.repository.*;
import com.anabada.fleaflea.domain.chat.service.ChatService;
import com.anabada.fleaflea.domain.friendship.domain.*;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.friendship.service.FriendshipService;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.global.exception.*;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@AutoConfigureMockMvc
@PostgresIntegrationTest
class ChatServiceTest {
    @Autowired ChatService service;
    @Autowired FriendshipService friendshipService;
    @Autowired MemberRepository members;
    @Autowired FriendshipRepository friendships;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatMessageRepository messages;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @MockitoBean ImageService images;
    @MockitoBean NotificationSseService sse;
    Member a, b, outsider;
    Friendship friendship;

    @BeforeEach
    void prepare() {
        messages.deleteAll(); rooms.deleteAll(); friendships.deleteAll(); members.deleteAll();
        a = members.save(Member.create("a@test.local", "pw", "a"));
        b = members.save(Member.create("b@test.local", "pw", "b"));
        outsider = members.save(Member.create("c@test.local", "pw", "c"));
        friendship = friendships.save(Friendship.create(b, a, FriendshipStatus.ACCEPTED));
    }

    @Test
    void onlyAcceptedFriendsCanOpenAndReversedPairReusesRoom() {
        Room room = service.open(a.getMemberId(), b.getMemberId());
        assertThat(service.open(b.getMemberId(), a.getMemberId()).id()).isEqualTo(room.id());
        assertThat(rooms.count()).isEqualTo(1);
        forbidden(() -> service.open(a.getMemberId(), outsider.getMemberId()), ErrorCode.CHAT_FRIEND_REQUIRED);
        forbidden(() -> service.open(a.getMemberId(), a.getMemberId()), ErrorCode.CHAT_FRIEND_REQUIRED);
        friendships.save(Friendship.create(a, outsider, FriendshipStatus.PENDING));
        forbidden(() -> service.open(a.getMemberId(), outsider.getMemberId()), ErrorCode.CHAT_FRIEND_REQUIRED);
    }

    @Test
    void outsidersCannotReadSendOrMarkRead() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        Message m = send(a, id, "hello");
        forbidden(() -> service.detail(outsider.getMemberId(), id), ErrorCode.CHAT_NOT_PARTICIPANT);
        forbidden(() -> service.send(outsider.getMemberId(), id, request("hello")), ErrorCode.CHAT_NOT_PARTICIPANT);
        forbidden(() -> service.history(outsider.getMemberId(), id, null, null, 30), ErrorCode.CHAT_NOT_PARTICIPANT);
        forbidden(() -> service.read(outsider.getMemberId(), id, m.id()), ErrorCode.CHAT_NOT_PARTICIPANT);
    }

    @Test
    void deletingFriendPreservesHistoryAndBlocksNewMessages() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        Message m = send(a, id, "약속 장소");
        friendshipService.deleteFriend(a.getMemberId(), friendship.getFriendshipId());
        assertThat(service.detail(a.getMemberId(), id).canSend()).isFalse();
        assertThat(service.history(b.getMemberId(), id, null, null, 30).messages()).containsExactly(m);
        forbidden(() -> send(a, id, "새 메시지"), ErrorCode.CHAT_FRIEND_REQUIRED);
        service.read(b.getMemberId(), id, m.id());
    }

    @Test
    void retryIsIdempotentAndChangedContentIsRejected() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        SendMessage request = request("hello");
        Message first = service.send(a.getMemberId(), id, request);
        assertThat(service.send(a.getMemberId(), id, request)).isEqualTo(first);
        assertThat(messages.count()).isEqualTo(1);
        forbidden(() -> service.send(a.getMemberId(), id, new SendMessage("changed", request.clientMessageId())),
                ErrorCode.CHAT_DUPLICATE_MESSAGE_CONFLICT);
    }

    @Test
    void paginationCatchUpAndReadWatermark() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        Message first = send(a, id, "1"); Message second = send(a, id, "2"); Message third = send(a, id, "3");
        assertThat(service.list(b.getMemberId(), 0, 20).rooms().getFirst().unreadCount()).isEqualTo(3);
        Messages newest = service.history(b.getMemberId(), id, null, null, 2);
        assertThat(newest.messages()).containsExactly(third, second);
        assertThat(newest.hasNext()).isTrue();
        assertThat(service.history(b.getMemberId(), id, newest.nextCursor(), null, 2).messages()).containsExactly(first);
        Messages catchUp = service.history(b.getMemberId(), id, null, 0L, 2);
        assertThat(catchUp.messages()).containsExactly(first, second);
        assertThat(service.history(b.getMemberId(), id, null, catchUp.nextCursor(), 2).messages()).containsExactly(third);
        service.read(b.getMemberId(), id, second.id()); service.read(b.getMemberId(), id, first.id());
        assertThat(service.detail(b.getMemberId(), id).myLastReadId()).isEqualTo(second.id());
        assertThat(service.list(b.getMemberId(), 0, 20).rooms().getFirst().unreadCount()).isEqualTo(1);
        Long otherRoom = service.open(b.getMemberId(), a.getMemberId()).id();
        assertThat(otherRoom).isEqualTo(id);
        forbidden(() -> service.history(b.getMemberId(), id, first.id(), 0L, 30), ErrorCode.INVALID_REQUEST);
    }

    @Test
    void invalidTextAndRateLimit() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        forbidden(() -> send(a, id, "  "), ErrorCode.INVALID_REQUEST);
        forbidden(() -> send(a, id, "a".repeat(2001)), ErrorCode.INVALID_REQUEST);
        for (int i = 0; i < 60; i++) send(a, id, "message " + i);
        forbidden(() -> send(a, id, "61"), ErrorCode.CHAT_RATE_LIMIT_EXCEEDED);
        assertThat(messages.count()).isEqualTo(60);
    }

    @Test
    void concurrentOpeningCreatesOneRoom() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var one = pool.submit(() -> { start.await(); return service.open(a.getMemberId(), b.getMemberId()).id(); });
            var two = pool.submit(() -> { start.await(); return service.open(b.getMemberId(), a.getMemberId()).id(); });
            start.countDown();
            assertThat(one.get(10, TimeUnit.SECONDS)).isEqualTo(two.get(10, TimeUnit.SECONDS));
            assertThat(rooms.count()).isEqualTo(1);
        }
    }

    @Test
    void concurrentRetriesPersistOnlyOneMessage() throws Exception {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        SendMessage request = request("one message");
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var one = pool.submit(() -> { start.await(); return service.send(a.getMemberId(), id, request); });
            var two = pool.submit(() -> { start.await(); return service.send(a.getMemberId(), id, request); });
            start.countDown();
            assertThat(one.get(10, TimeUnit.SECONDS)).isEqualTo(two.get(10, TimeUnit.SECONDS));
            assertThat(messages.count()).isEqualTo(1);
        }
    }

    @Test
    void concurrentSendDoesNotOverwriteReadState() throws Exception {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        Message first = send(a, id, "first");
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var sending = pool.submit(() -> { start.await(); return send(a, id, "second"); });
            var reading = pool.submit(() -> { start.await(); return service.read(b.getMemberId(), id, first.id()); });
            start.countDown();
            Message second = sending.get(10, TimeUnit.SECONDS);
            reading.get(10, TimeUnit.SECONDS);
            Room room = service.detail(b.getMemberId(), id);
            assertThat(room.myLastReadId()).isEqualTo(first.id());
            assertThat(room.lastMessageId()).isEqualTo(second.id());
            assertThat(room.unreadCount()).isEqualTo(1);
        }
    }

    @Test
    void sendsSseOnlyAfterCommitAndNotAfterRollback() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            send(a, id, "rolled back");
            verify(sse, never()).sendEvent(anyLong(), eq("chat-message"), any());
            status.setRollbackOnly();
        });
        assertThat(messages.count()).isZero();
        Message saved = send(a, id, "committed");
        verify(sse, timeout(3000)).sendEvent(a.getMemberId(), "chat-message", saved);
        verify(sse, timeout(3000)).sendEvent(b.getMemberId(), "chat-message", saved);
        verify(sse, never()).sendEvent(eq(outsider.getMemberId()), anyString(), any());
    }

    @Test
    void apiRequiresAuthenticationAndValidatesInputs() throws Exception {
        mvc.perform(get("/api/v1/chat/rooms")).andExpect(status().isUnauthorized());
        var auth = new UsernamePasswordAuthenticationToken(a.getMemberId(), null, List.of());
        mvc.perform(post("/api/v1/chat/rooms").with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON).content("{\"friendId\":0}"))
                .andExpect(status().isBadRequest());
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        mvc.perform(post("/api/v1/chat/rooms/" + id + "/messages").with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\",\"clientMessageId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/chat/rooms?page=-1").with(authentication(auth)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/chat/rooms/" + id).with(authentication(
                new UsernamePasswordAuthenticationToken(outsider.getMemberId(), null, List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotMarkMessageFromAnotherRoomAsRead() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        friendships.save(Friendship.create(a, outsider, FriendshipStatus.ACCEPTED));
        Long otherRoom = service.open(a.getMemberId(), outsider.getMemberId()).id();
        Message otherMessage = send(a, otherRoom, "another room");
        forbidden(() -> service.read(b.getMemberId(), id, otherMessage.id()), ErrorCode.CHAT_MESSAGE_NOT_FOUND);
    }

    private SendMessage request(String content) { return new SendMessage(content, UUID.randomUUID()); }
    private Message send(Member member, Long roomId, String content) { return service.send(member.getMemberId(), roomId, request(content)); }
    private void forbidden(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
