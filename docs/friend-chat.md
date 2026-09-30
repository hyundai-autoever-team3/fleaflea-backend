# 친구 채팅 API (#153)

친구 요청을 수락한 두 사람끼리 텍스트 메시지를 주고받는다. 기존 알림 SSE 연결을 함께 사용한다.

## 정책

- `ACCEPTED` 친구만 방 생성과 전송이 가능하다. 요청 방향은 관계없다.
- 두 회원당 방 하나를 사용한다. 동시에 방을 만들어도 같은 방을 반환한다.
- 친구 삭제 뒤에는 과거 대화 조회와 읽음 처리를 허용하고 새 전송은 막는다. 다시 친구가 되면 같은 방에서 대화한다.
- 발신자는 액세스 토큰에서 확인한다. 다른 사람의 방에는 접근할 수 없다.
- 텍스트는 1~2000자이며 공백만 보낼 수 없다. 회원당 분당 60개의 새 메시지까지 허용한다.
- 사진, 물건 카드 및 웹·앱 푸시는 이번 구현에 포함하지 않는다.

## API

모든 요청에 `Authorization: Bearer <access token>`을 보낸다.

| 메서드 | 경로 | 요청 / 응답 |
|---|---|---|
| POST | `/api/v1/chat/rooms` | `{ "friendId": 2 }` → 채팅방 상세 |
| GET | `/api/v1/chat/rooms?page=0&size=20` | `{ rooms, hasNext }`, 최근 활동순 |
| GET | `/api/v1/chat/rooms/{roomId}` | 채팅방 상세 |
| POST | `/api/v1/chat/rooms/{roomId}/messages` | `{ "content": "어디서 만날까?", "clientMessageId": "UUID" }` → 메시지 |
| GET | `/api/v1/chat/rooms/{roomId}/messages?size=30` | 최신 메시지부터 내림차순 |
| GET | `/api/v1/chat/rooms/{roomId}/messages?beforeId=123&size=30` | 123보다 이전 메시지, 내림차순 |
| GET | `/api/v1/chat/rooms/{roomId}/messages?afterId=123&size=30` | 123 이후 메시지, 오름차순 |
| PATCH | `/api/v1/chat/rooms/{roomId}/read` | `{ "messageId": 123 }` → 읽음 상태 |

메시지 조회 응답은 `{ messages, nextCursor, hasNext }`다. `beforeId`, `afterId`는 동시에 사용하지 않는다.
`hasNext`가 true면 `nextCursor`를 같은 방향의 커서로 전달해 다음 묶음을 조회한다. 새 동기화는 `afterId=0`부터 가능하다.

채팅방 상세에는 상대의 `friendId`, `nickname`, `profileImageUrl`, `canSend`, 마지막 메시지,
`myLastReadId`, `friendLastReadId`, `unreadCount`를 반환한다. `canSend=false`면 입력창을 비활성화한다.
메시지에는 `id`, `roomId`, `senderId`, `content`, `clientMessageId`, `createdAt`이 포함된다.
목록은 최대 100개 단위로 조회하며 상대 정보와 안 읽은 수는 묶어서 조회한다.

## SSE 및 프론트 연동

`GET /api/v1/notifications/subscribe`에 fetch 기반 SSE 클라이언트로 연결한다.
기존 `notification`, `connect`, heartbeat 이벤트는 그대로 유지한다.

- `chat-message`: 메시지 응답과 같은 JSON. 발신자와 수신자의 모든 연결에 전달한다.
- `chat-read`: `{ "roomId": 1, "memberId": 2, "lastReadMessageId": 123 }`. 두 참여자에게 전달한다.
- 메시지 ID 기준으로 중복을 제거하고 화면에서는 ID 오름차순으로 정렬한다.
- 전송 버튼을 눌렀을 때 UUID를 한 번 만들고 네트워크 재시도에는 같은 값을 쓴다. 같은 ID에 다른 내용은 409로 거절한다.
- 서버 DB 커밋 후 이벤트를 보낸다. 수신자가 접속하지 않았거나 이벤트 전달이 실패해도 메시지는 DB에 남는다.
- 재접속 시 SSE 연결을 먼저 열고 채팅 목록과 각 방의 누락 메시지를 조회한다. 조회 중 도착한 이벤트도 ID로 중복 제거한다.
- **복구 커서는 DB의 `afterId` 조회로 확인한 마지막 ID를 보관한다.** 비동기 SSE 이벤트는 순서가 바뀔 수 있으므로 SSE에서 본 최대 ID만 복구 커서로 삼지 않는다. 새 이벤트를 받으면 DB 동기화도 수행한다.
- 읽음 처리는 사용자가 실제로 확인한 메시지 ID로 요청한다. 낮은 ID를 다시 보내도 읽음 위치는 뒤로 가지 않는다.
- 브라우저가 닫혀 있을 때의 알림은 제공하지 않는다. 웹 푸시와 앱 푸시는 후속 작업이다.

## 서버 운영

- V18 마이그레이션으로 테이블과 인덱스를 추가한다. 기존 거래·친구 데이터는 변경하지 않는다.
- 친구 삭제와 전송은 같은 친구 관계 행을 잠가 삭제 완료 후 전송이 통과하지 않게 한다.
- 메시지 전송과 읽음 처리는 채팅방 행을 잠가 메시지 순서와 읽음 상태를 보호한다.
- SSE 연결 저장소는 현재 단일 서버의 메모리에 있다. 여러 서버로 확장할 때는 서버 간 이벤트 전달 수단이 필요하다.
- SSE 이벤트는 영속 큐가 아니므로 전달 실패는 메시지 조회로 복구한다.
- SSE의 기존 event ID는 연결 ID이며 채팅 메시지 복구 커서가 아니다.

## 오류

| 코드 | HTTP | 의미 |
|---|---|---|
| `CHAT_FRIEND_REQUIRED` | 403 | 친구가 아니거나 본인에게 전송 |
| `CHAT_NOT_PARTICIPANT` | 403 | 다른 사람의 방에 접근 |
| `CHAT_ROOM_NOT_FOUND` | 404 | 존재하지 않는 방 |
| `CHAT_MESSAGE_NOT_FOUND` | 404 | 읽으려는 메시지가 해당 방에 없음 |
| `CHAT_DUPLICATE_MESSAGE_CONFLICT` | 409 | 같은 클라이언트 ID에 다른 내용 |
| `CHAT_RATE_LIMIT_EXCEEDED` | 429 | 전송 빈도 초과 |

빈 메시지, 2000자 초과, 잘못된 UUID와 커서 입력은 400으로 처리한다.
