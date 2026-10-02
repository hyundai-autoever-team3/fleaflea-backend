package com.anabada.fleaflea.domain.chat;

import com.anabada.fleaflea.global.dto.CursorPageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatRoomResponse;
import com.anabada.fleaflea.domain.chat.exception.ChatRateLimitExceededException;
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
    @DisplayName("수락된 친구만 채팅을 시작하며 요청 방향이 달라도 같은 방을 사용한다")
    void onlyAcceptedFriendsCanOpenAndReversedPairReusesRoom() {
        ChatRoomResponse room = service.open(a.getMemberId(), b.getMemberId());
        assertThat(service.open(b.getMemberId(), a.getMemberId()).id()).isEqualTo(room.id());
        assertThat(rooms.count()).isEqualTo(1);
        forbidden(() -> service.open(a.getMemberId(), outsider.getMemberId()), ErrorCode.CHAT_FRIEND_REQUIRED);
        forbidden(() -> service.open(a.getMemberId(), a.getMemberId()), ErrorCode.CHAT_FRIEND_REQUIRED);
        friendships.save(Friendship.create(a, outsider, FriendshipStatus.PENDING));
        forbidden(() -> service.open(a.getMemberId(), outsider.getMemberId()), ErrorCode.CHAT_FRIEND_REQUIRED);
    }

    @Test
    @DisplayName("채팅방 참여자가 아니면 조회·전송·읽음 처리를 할 수 없다")
    void outsidersCannotReadSendOrMarkRead() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        ChatMessageResponse m = send(a, id, "hello");
        forbidden(() -> service.detail(outsider.getMemberId(), id), ErrorCode.CHAT_NOT_PARTICIPANT);
        forbidden(() -> service.send(outsider.getMemberId(), id, request("hello")), ErrorCode.CHAT_NOT_PARTICIPANT);
        forbidden(() -> service.history(outsider.getMemberId(), id, null, null, 30), ErrorCode.CHAT_NOT_PARTICIPANT);
        forbidden(() -> service.read(outsider.getMemberId(), id, m.id()), ErrorCode.CHAT_NOT_PARTICIPANT);
    }

    @Test
    @DisplayName("친구 삭제 후 대화 이력은 보존하고 새 메시지 전송은 차단한다")
    void deletingFriendPreservesHistoryAndBlocksNewMessages() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        ChatMessageResponse m = send(a, id, "약속 장소");
        friendshipService.deleteFriend(a.getMemberId(), friendship.getFriendshipId());
        assertThat(service.detail(a.getMemberId(), id).canSend()).isFalse();
        assertThat(service.history(b.getMemberId(), id, null, null, 30).content()).containsExactly(m);
        forbidden(() -> send(a, id, "새 메시지"), ErrorCode.CHAT_FRIEND_REQUIRED);
        service.read(b.getMemberId(), id, m.id());
    }

    @Test
    @DisplayName("같은 메시지 재전송은 중복 저장하지 않고 내용이 다르면 거절한다")
    void retryIsIdempotentAndChangedContentIsRejected() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        ChatMessageSendRequest request = request("hello");
        ChatMessageResponse first = service.send(a.getMemberId(), id, request);
        assertThat(service.send(a.getMemberId(), id, request)).isEqualTo(first);
        assertThat(messages.count()).isEqualTo(1);
        forbidden(() -> service.send(a.getMemberId(), id, new ChatMessageSendRequest("changed", request.clientMessageId())),
                ErrorCode.CHAT_DUPLICATE_MESSAGE_CONFLICT);
    }

    @Test
    @DisplayName("커서로 과거·누락 메시지를 조회하고 읽음 위치는 뒤로 이동하지 않는다")
    void paginationCatchUpAndReadWatermark() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        ChatMessageResponse first = send(a, id, "1"); ChatMessageResponse second = send(a, id, "2"); ChatMessageResponse third = send(a, id, "3");
        assertThat(service.list(b.getMemberId(), 0, 20).rooms().getFirst().unreadCount()).isEqualTo(3);
        CursorPageResponse<ChatMessageResponse> newest = service.history(b.getMemberId(), id, null, null, 2);
        assertThat(newest.content()).containsExactly(third, second);
        assertThat(newest.hasNext()).isTrue();
        assertThat(service.history(b.getMemberId(), id, newest.nextCursor(), null, 2).content()).containsExactly(first);
        CursorPageResponse<ChatMessageResponse> catchUp = service.history(b.getMemberId(), id, null, 0L, 2);
        assertThat(catchUp.content()).containsExactly(first, second);
        assertThat(service.history(b.getMemberId(), id, null, catchUp.nextCursor(), 2).content()).containsExactly(third);
        service.read(b.getMemberId(), id, second.id()); service.read(b.getMemberId(), id, first.id());
        assertThat(service.detail(b.getMemberId(), id).myLastReadId()).isEqualTo(second.id());
        assertThat(service.list(b.getMemberId(), 0, 20).rooms().getFirst().unreadCount()).isEqualTo(1);
        Long otherRoom = service.open(b.getMemberId(), a.getMemberId()).id();
        assertThat(otherRoom).isEqualTo(id);
        forbidden(() -> service.history(b.getMemberId(), id, first.id(), 0L, 30), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("분당 전송 횟수를 초과하면 채팅 전용 예외로 거절한다")
    void rateLimitRejectsExcessMessages() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        for (int i = 0; i < 60; i++) send(a, id, "message " + i);
        assertThatThrownBy(() -> send(a, id, "61"))
                .isInstanceOfSatisfying(ChatRateLimitExceededException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED));
        assertThat(messages.count()).isEqualTo(60);
    }

    @Test
    @DisplayName("메시지 API는 잘못된 입력을 저장 전에 거절하고 2000자는 허용한다")
    void messageApiValidatesRequestBeforeSaving() throws Exception {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        var auth = new UsernamePasswordAuthenticationToken(a.getMemberId(), null, List.of());
        String clientId = UUID.randomUUID().toString();
        List<String> invalidBodies = List.of(
                "{\"content\":null,\"clientMessageId\":\"%s\"}".formatted(clientId),
                "{\"clientMessageId\":\"%s\"}".formatted(clientId),
                "{\"content\":\"\",\"clientMessageId\":\"%s\"}".formatted(clientId),
                "{\"content\":\"   \",\"clientMessageId\":\"%s\"}".formatted(clientId),
                "{\"content\":\"%s\",\"clientMessageId\":\"%s\"}".formatted("a".repeat(2001), clientId),
                "{\"content\":\"hello\",\"clientMessageId\":null}",
                "{\"content\":\"hello\"}"
        );
        for (String body : invalidBodies) {
            mvc.perform(post("/api/v1/chat/rooms/" + id + "/messages").with(authentication(auth))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(messages.count()).isZero();
        mvc.perform(post("/api/v1/chat/rooms/" + id + "/messages").with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"%s\",\"clientMessageId\":\"%s\"}".formatted("a".repeat(2000), clientId)))
                .andExpect(status().isOk());
        assertThat(messages.findAll()).singleElement()
                .satisfies(message -> assertThat(message.getContent()).hasSize(2000));
    }

    @Test
    @DisplayName("동시에 채팅을 시작해도 방은 하나만 생성한다")
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
    @DisplayName("같은 메시지를 동시에 재전송해도 하나만 저장한다")
    void concurrentRetriesPersistOnlyOneMessage() throws Exception {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        ChatMessageSendRequest request = request("one message");
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
    @DisplayName("메시지 전송과 읽음 처리가 동시에 발생해도 읽음 상태를 보존한다")
    void concurrentSendDoesNotOverwriteReadState() throws Exception {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        ChatMessageResponse first = send(a, id, "first");
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var sending = pool.submit(() -> { start.await(); return send(a, id, "second"); });
            var reading = pool.submit(() -> { start.await(); return service.read(b.getMemberId(), id, first.id()); });
            start.countDown();
            ChatMessageResponse second = sending.get(10, TimeUnit.SECONDS);
            reading.get(10, TimeUnit.SECONDS);
            ChatRoomResponse room = service.detail(b.getMemberId(), id);
            assertThat(room.myLastReadId()).isEqualTo(first.id());
            assertThat(room.lastMessageId()).isEqualTo(second.id());
            assertThat(room.unreadCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("커밋된 메시지만 참여자에게 SSE로 전달하고 롤백된 메시지는 전달하지 않는다")
    void sendsSseOnlyAfterCommitAndNotAfterRollback() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            send(a, id, "rolled back");
            verify(sse, never()).sendEvent(anyLong(), eq("chat-message"), any());
            status.setRollbackOnly();
        });
        assertThat(messages.count()).isZero();
        ChatMessageResponse saved = send(a, id, "committed");
        verify(sse, timeout(3000)).sendEvent(a.getMemberId(), "chat-message", saved);
        verify(sse, timeout(3000)).sendEvent(b.getMemberId(), "chat-message", saved);
        verify(sse, never()).sendEvent(eq(outsider.getMemberId()), anyString(), any());
    }

    @Test
    @DisplayName("채팅 API는 인증·입력값·참여자 권한을 검증한다")
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
    @DisplayName("다른 채팅방의 메시지로 읽음 처리를 할 수 없다")
    void cannotMarkMessageFromAnotherRoomAsRead() {
        Long id = service.open(a.getMemberId(), b.getMemberId()).id();
        friendships.save(Friendship.create(a, outsider, FriendshipStatus.ACCEPTED));
        Long otherRoom = service.open(a.getMemberId(), outsider.getMemberId()).id();
        ChatMessageResponse otherMessage = send(a, otherRoom, "another room");
        forbidden(() -> service.read(b.getMemberId(), id, otherMessage.id()), ErrorCode.CHAT_MESSAGE_NOT_FOUND);
    }

    private ChatMessageSendRequest request(String content) { return new ChatMessageSendRequest(content, UUID.randomUUID()); }
    private ChatMessageResponse send(Member member, Long roomId, String content) { return service.send(member.getMemberId(), roomId, request(content)); }
    private void forbidden(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
