# 친구 채팅 API (#153)

친구 요청을 수락한 두 사람끼리 텍스트 메시지를 주고받는다. 채팅 전송·수신과 읽음 처리는 WebSocket + STOMP를 사용한다. 일반 알림은 기존 SSE를 유지한다.

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
| GET | `/api/v1/chat/rooms/{roomId}/messages?size=30` | 최신 메시지부터 내림차순 |
| GET | `/api/v1/chat/rooms/{roomId}/messages?beforeId=123&size=30` | 123보다 이전 메시지, 내림차순 |
| GET | `/api/v1/chat/rooms/{roomId}/messages?afterId=123&size=30` | 123 이후 메시지, 오름차순 |

메시지 조회 응답은 `{ content, nextCursor, hasNext }`다. `beforeId`, `afterId`는 동시에 사용하지 않는다.
`hasNext`가 true면 `nextCursor`를 같은 방향의 커서로 전달해 다음 묶음을 조회한다. 새 동기화는 `afterId=0`부터 가능하다.

채팅방 상세에는 상대의 `friendId`, `nickname`, `profileImageUrl`, `canSend`, 마지막 메시지,
`myLastReadId`, `friendLastReadId`, `unreadCount`를 반환한다. `canSend=false`면 입력창을 비활성화한다.
메시지에는 `id`, `roomId`, `senderId`, `content`, `clientMessageId`, `createdAt`이 포함된다.
목록은 최대 100개 단위로 조회하며 상대 정보와 안 읽은 수는 묶어서 조회한다.

## WebSocket 및 프론트 연동

연결 주소는 개발 환경에서 `ws://localhost:8080/ws/chat`, 배포 환경에서
`wss://api.fleaflea.app/ws/chat`이다. 일반 WebSocket에 STOMP 프레임을 얹는 방식이며
SockJS는 사용하지 않는다. 웹·안드로이드·iOS에서 STOMP를 지원하는 클라이언트를 사용한다.

HTTP 업그레이드 요청에는 토큰이 없어도 되지만, STOMP CONNECT 헤더에
`Authorization: Bearer <access token>`을 반드시 전달한다. 토큰을 URL에 넣지 않는다.
허용 웹 Origin은 기존 `cors.allowed-origins` 설정을 따른다.
연결 후 10초 안에 CONNECT 인증을 완료해야 하며, STOMP ERROR를 받으면 연결을 종료하고 원인을 처리한다.

CONNECTED 이후 아래 세 경로를 구독하고 메시지를 전송한다.

| 용도 | STOMP 경로 | 본문 |
|---|---|---|
| 메시지 전송 (SEND) | `/app/chat/rooms/{roomId}/messages` | `{ "content": "어디서 만날까?", "clientMessageId": "UUID" }` |
| 입력 상태 (SEND) | `/app/chat/rooms/{roomId}/typing` | `{ "typing": true }` 또는 `{ "typing": false }` |
| 읽음 처리 (SEND) | `/app/chat/rooms/{roomId}/read` | `{ "messageId": 123 }` |
| 실시간 이벤트 (SUBSCRIBE) | `/user/queue/chat` | `{ "type": "chat-message", "payload": ... }`, type은 `chat-message`·`chat-read`·`chat-typing` 중 하나 |
| 요청 결과 (SUBSCRIBE) | `/user/queue/chat-acks` | 저장된 메시지 또는 읽음 상태 응답 |
| 요청 오류 (SUBSCRIBE) | `/user/queue/chat-errors` | `{ "code": "CHAT_FRIEND_REQUIRED", "message": "..." }` |

- `chat-message` payload는 메시지 응답과 같다. 두 참여자의 모든 접속 기기에 전달한다.
- `chat-typing` payload는 `{ "roomId": 1, "memberId": 2, "typing": true }`다.
  채팅방 참여자이면서 현재 친구인 회원만 전송할 수 있고, 상대방의 접속 기기에만 전달한다.
  DB에 저장하지 않으며 요청 성공 응답(`chat-acks`)이나 재접속 시 복구도 제공하지 않는다.
- 입력 시작 시 `typing=true`를 보내고 계속 입력하는 동안 최대 1초에 한 번 갱신한다.
  입력을 멈추거나 입력창이 비거나 메시지를 보내거나 방을 나가면 `typing=false`를 보낸다.
- 수신 화면은 현재 방과 상대 회원 ID가 일치할 때만 표시한다. 마지막 `true` 수신 후
  3초 동안 갱신이 없으면 숨기고, `false` 수신·메시지 수신·방 변경·소켓 종료 시에도 숨긴다.
  자동 숨김은 클라이언트에서 구현해야 한다. 여러 기기의 상태를 서버에서 합산하지 않으며
  마지막으로 받은 상태를 기준으로 표시한다.
- `chat-read` payload는 `{ "roomId": 1, "memberId": 2, "lastReadMessageId": 123 }`다.
- 요청 결과와 오류는 요청을 보낸 세션에만 전달한다. 본인 경로만 구독할 수 있고,
  `/user/{다른 회원}/...`, `/queue/...`, `/topic/...` 구독 및 브로커 직접 SEND는 차단한다.
- 발신자 ID는 CONNECT에서 인증한 Principal에서 추출한다. 본문에 발신자 ID를 보내지 않는다.
- DB 커밋 후 실시간 이벤트를 보낸다. 상대가 오프라인이거나 전달이 실패해도 메시지는 DB에 남는다.
- `chat-acks`는 애플리케이션의 저장 결과다. STOMP ACK나 영속 메시지 큐의 전달 보장과는 다르다.
- UUID는 전송 버튼을 누를 때 한 번 만들고, 결과를 못 받은 재시도에는 같은 값을 사용한다.
  같은 UUID로 같은 내용을 다시 보내면 기존 저장 결과를 반환한다. 실시간 이벤트는 다시 발행하지 않는다.
- 발신자는 이벤트와 요청 결과에서 같은 메시지를 받을 수 있으므로 메시지 ID 기준으로 중복 제거한다.
- 읽음 처리는 실제로 확인한 메시지 ID를 보낸다. 읽음 위치는 뒤로 이동하지 않는다.
- SEND와 SUBSCRIBE에서도 토큰 만료를 검사한다. 만료된 연결은 주기적으로 종료한다(최대 약 1초 지연).
  인증 실패나 허용되지 않은 전송·구독 경로는 연결을 종료한다(WebSocket close code 1008).
  `chat-errors`에서 받는 요청 오류와 구분한다.
- 토큰이 만료되면 기존 REST reissue API로 액세스 토큰을 갱신하고 새 토큰으로 재연결한다.
  같은 연결에서 CONNECT를 다시 보내 토큰을 바꾸지 않는다.
- 로그아웃이나 계정 전환 시 기존 STOMP 클라이언트를 종료한다(웹 예시: `client.deactivate()`).
- 서버·클라이언트 heartbeat 간격은 10초를 기준으로 맞춘다.
- 재접속 후 먼저 구독을 복원하고 채팅방 목록과 REST 이력을 다시 조회한다.
  `afterId` 조회를 끝까지 수행하고, 조회 중 도착한 이벤트도 ID로 중복 제거한다.
- **복구 커서는 REST `afterId` 조회로 확인한 마지막 ID를 보관한다.** 여러 트랜잭션의 이벤트는
  도착 순서가 바뀔 수 있으므로 실시간으로 본 최대 ID만 복구 커서로 사용하지 않는다.
- 브라우저나 앱이 종료된 상태의 푸시는 이번 범위에 포함하지 않는다.

### 웹 클라이언트 예시 (@stomp/stompjs)

```javascript
const client = new Client({
  brokerURL: 'wss://api.fleaflea.app/ws/chat',
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  reconnectDelay: 5000,
  beforeConnect: async () => {
    // 앱의 기존 토큰 관리 로직에서 유효한 액세스 토큰을 가져온다.
    client.connectHeaders = { Authorization: `Bearer ${await getValidAccessToken()}` };
  },
  onConnect: () => {
    client.subscribe('/user/queue/chat', frame => handleChatEvent(JSON.parse(frame.body)));
    client.subscribe('/user/queue/chat-acks', frame => handleChatResult(JSON.parse(frame.body)));
    client.subscribe('/user/queue/chat-errors', frame => handleChatError(JSON.parse(frame.body)));
    // 기존 REST API로 채팅방 목록과 누락 메시지를 동기화한다.
    syncChatHistory();
  },
});
client.activate();

// 연결 완료 이후 전송. 재시도에는 아래 clientMessageId를 그대로 사용한다.
const clientMessageId = crypto.randomUUID();
client.publish({
  destination: `/app/chat/rooms/${roomId}/messages`,
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ content: '어디서 만날까?', clientMessageId }),
});
```

`getValidAccessToken`, `handleChatEvent`, `handleChatResult`, `handleChatError`,
`syncChatHistory`는 프론트의 토큰 관리·화면 처리 로직으로 구현한다.
채팅의 기존 POST messages / PATCH read REST API는 제거되었으므로 위 SEND 경로로 변경한다.
일반 알림 `GET /api/v1/notifications/subscribe`와 그 이벤트는 유지한다.

## 서버 운영

- V18 채팅 테이블을 그대로 사용한다. 이번 전환에 새 마이그레이션은 필요 없다.
- 친구 삭제와 전송은 같은 친구 관계 행을 잠가 삭제 완료 후 전송이 통과하지 않게 한다.
- 메시지 전송과 읽음 처리는 채팅방 행을 잠가 메시지 순서와 읽음 상태를 보호한다.
- 각 서버의 WebSocket 연결은 Spring 기본 브로커로 관리한다.
  `CHAT_REDIS_ENABLED=false`(기본값)이면 현재 서버의 연결에만 이벤트를 전달하므로 단일 백엔드에서 사용한다.
- 여러 백엔드에 사용자가 나뉘어 접속하는 환경에서는 모든 백엔드에 `CHAT_REDIS_ENABLED=true`를 설정한다.
  서버들은 같은 Redis에 연결하고 같은 `CHAT_REDIS_CHANNEL_PREFIX`(기본값 `fleaflea:chat`)를 사용한다.
  Redis Pub/Sub으로 이벤트를 공유한 뒤 각 서버가 자신의 연결로 전달하며, 프론트의 STOMP 경로·응답 형식은 바뀌지 않는다.
  운영·개발·테스트 환경의 접두어는 서로 구분한다.
- EC2 compose는 `/etc/fleaflea/fleaflea.env`와 `/etc/fleaflea/redis.env`를 앱 컨테이너에 전달한다.
  채팅 환경변수는 서버 환경 파일에서 관리하며, 변경한 환경변수는 컨테이너를 재생성해야 반영된다.
  PR 병합만으로 Redis 채팅 전달이 자동 활성화되지는 않는다.
- Redis Pub/Sub은 오프라인 이벤트를 보관하거나 재전송하지 않는다. 발행 실패 후에도 이미 커밋된 메시지는 DB에 남으며,
  클라이언트는 REST 커서 조회로 복구한다. 입력 상태 이벤트는 복구 대상이 아니다.
- 프레임 크기는 16KB, 세션별 전송 버퍼는 256KB로 제한한다. 메시지 내용 제한은 기존 2000자다.
- EC2에서 Nginx 설정을 별도로 관리한다. `/ws/chat` 경로에 HTTP/1.1 Upgrade 헤더가 필요하며,
  설정 변경 시 `sudo nginx -t` 후 `sudo systemctl reload nginx`를 실행한다. 서버 설정은 자동 배포 대상이 아니다.
- 클라이언트 STOMP 구독과 heartbeat, 토큰 재연결 처리를 연동해야 배포 환경에서 채팅이 동작한다.

## 오류

| 코드 | HTTP | 의미 |
|---|---|---|
| `CHAT_FRIEND_REQUIRED` | 403 | 친구가 아니거나 본인에게 전송 |
| `CHAT_NOT_PARTICIPANT` | 403 | 다른 사람의 방에 접근 |
| `CHAT_ROOM_NOT_FOUND` | 404 | 존재하지 않는 방 |
| `CHAT_MESSAGE_NOT_FOUND` | 404 | 읽으려는 메시지가 해당 방에 없음 |
| `CHAT_DUPLICATE_MESSAGE_CONFLICT` | 409 | 같은 클라이언트 ID에 다른 내용 |
| `CHAT_RATE_LIMIT_EXCEEDED` | 429 | 전송 빈도 초과 |

빈 메시지, 2000자 초과, 잘못된 UUID는 소켓 `chat-errors`로 `INVALID_REQUEST`를 반환한다.
REST 조회의 잘못된 커서는 기존 HTTP 400으로 처리한다. 위 HTTP 상태는 도메인 오류의 분류이며 소켓 응답에는 HTTP 상태 대신 code와 message를 전달한다.
