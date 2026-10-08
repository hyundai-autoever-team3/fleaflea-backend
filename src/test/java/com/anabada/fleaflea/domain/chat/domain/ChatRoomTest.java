package com.anabada.fleaflea.domain.chat.domain;

import com.anabada.fleaflea.domain.chat.exception.ChatNotParticipantException;
import com.anabada.fleaflea.fixture.ChatFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRoomTest {

    @Test
    @DisplayName("회원 순서가 달라도 작은 ID와 큰 ID로 동일한 참여자 쌍을 구성한다")
    void create_ordersParticipantIds() {
        ChatRoom chatRoom = ChatFixture.createChatRoom(2L, 1L);

        assertThat(chatRoom.getMemberLowId()).isEqualTo(1L);
        assertThat(chatRoom.getMemberHighId()).isEqualTo(2L);
        assertThat(chatRoom.getOtherMemberId(1L)).isEqualTo(2L);
        assertThat(chatRoom.getOtherMemberId(2L)).isEqualTo(1L);
    }

    @ParameterizedTest(name = "참여하지 않은 회원 ID={0}")
    @NullSource
    @ValueSource(longs = {0L, -1L, 3L})
    @DisplayName("참여자가 아닌 회원은 상대 회원과 읽음 상태를 조회하거나 변경할 수 없다")
    void participantMethods_rejectNonParticipant(Long memberId) {
        ChatRoom chatRoom = ChatFixture.createChatRoom(1L, 2L);

        assertThatThrownBy(() -> chatRoom.getOtherMemberId(memberId))
                .isInstanceOf(ChatNotParticipantException.class);
        assertThatThrownBy(() -> chatRoom.getLastReadMessageId(memberId))
                .isInstanceOf(ChatNotParticipantException.class);
        assertThatThrownBy(() -> chatRoom.markMessagesAsRead(memberId, 10L))
                .isInstanceOf(ChatNotParticipantException.class);

        assertThat(chatRoom.getLastReadMessageId(1L)).isZero();
        assertThat(chatRoom.getLastReadMessageId(2L)).isZero();
    }

    @Test
    @DisplayName("과거 메시지를 읽음 처리해도 두 참여자의 마지막 읽음 위치는 뒤로 이동하지 않는다")
    void markMessagesAsRead_preservesEachParticipantWatermark() {
        ChatRoom chatRoom = ChatFixture.createChatRoom(1L, 2L);

        chatRoom.markMessagesAsRead(1L, 20L);
        chatRoom.markMessagesAsRead(1L, 10L);
        chatRoom.markMessagesAsRead(2L, 15L);
        chatRoom.markMessagesAsRead(2L, 5L);

        assertThat(chatRoom.getLastReadMessageId(1L)).isEqualTo(20L);
        assertThat(chatRoom.getLastReadMessageId(2L)).isEqualTo(15L);
    }
}
