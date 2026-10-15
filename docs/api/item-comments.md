# 상품 댓글·대댓글

마켓 참여자만 조회·작성할 수 있으며, 참여 중인 작성자만 수정·삭제할 수 있다.
판매자나 마켓 개설자에게 다른 작성자의 댓글 수정·삭제 권한을 부여하지 않는다.
일반 댓글과 대댓글 모두 내용은 필수이며 최대 2,000자다. 저장할 때 앞뒤 공백을 제거한다.

## API

| 메서드 | 주소 | 용도 |
| --- | --- | --- |
| GET | `/api/v1/items/{itemId}/comments?page=0&size=10` | 일반 댓글 목록 |
| GET | `/api/v1/items/{itemId}/comments?parentId={commentId}&page=0&size=10` | 해당 댓글의 대댓글 목록 |
| POST | `/api/v1/items/{itemId}/comments` | 댓글·대댓글 작성 |
| PATCH | `/api/v1/items/{itemId}/comments/{commentId}` | 본인 댓글·대댓글 수정 |
| DELETE | `/api/v1/items/{itemId}/comments/{commentId}` | 본인 댓글·대댓글 삭제 |

목록은 기존 `PageResponse` 형식이며 작성 시각·ID 오름차순으로 정렬한다.
`page`는 0부터 시작하고 `size`는 1~100이다. 대댓글은 별도로 페이지 조회하므로 부모 댓글 조회 시 모든 답글을 가져오지 않는다.

작성 요청:

```json
{
  "content": "직거래 가능한가요?"
}
```

대댓글 작성 시 `parentId`를 추가한다. 수정 요청에는 `content`만 전달한다.
부모는 같은 상품의 일반 댓글이어야 한다. 대댓글에 다시 답글을 작성할 수 없다.
삭제된 부모에 대한 대댓글 조회·작성은 404로 처리한다.

작성은 201, 수정은 200, 삭제는 204로 응답한다. 작성·수정 응답은 아래 항목을 가진다.

| 필드 | 내용 |
| --- | --- |
| commentId / itemId / parentId | 댓글·상품·부모 ID, 일반 댓글의 parentId는 null |
| authorId / nickname | 작성자 ID와 현재 닉네임, 탈퇴한 회원은 ‘탈퇴한 회원’ 표시 |
| seller / mine | 상품 판매자 여부 / 요청한 회원이 작성자인지 여부 |
| content | 댓글 내용 |
| replyCount | 대댓글 수, 대댓글에서는 0 |
| createdAt | 작성 시각 |

## 삭제 처리

댓글·대댓글은 DB에서 하드 삭제한다. 대댓글 삭제 시 부모와 다른 답글은 유지한다.
부모 댓글 삭제 시 해당 부모의 모든 대댓글도 외래 키의 `ON DELETE CASCADE`로 함께 삭제한다.
삭제된 댓글 조회·수정·재삭제 요청은 404로 처리하며, 삭제 표시나 작성자 정보는 응답에 남기지 않는다.
상품 자체가 삭제되면 댓글과 대댓글도 DB에서 함께 제거한다.

## 오류

- 400: 요청 값 오류, `ITEM_COMMENT_INVALID_PARENT`(대댓글을 부모로 지정)
- 401: 인증 필요
- 403: `MARKET_NOT_PARTICIPANT`, `ITEM_COMMENT_NOT_OWNER`
- 404: `ITEM_NOT_FOUND`, `ITEM_COMMENT_NOT_FOUND`(다른 상품의 댓글 ID도 포함)

## 동시 요청 처리

댓글 쓰기는 기존 상품 삭제와 같은 상품 행의 DB 비관적 락을 사용한다.
부모 삭제와 대댓글 작성이 겹치면 먼저 락을 획득한 요청부터 처리한다.
상품 삭제가 먼저 완료되면 후속 댓글 작성은 상품 없음으로 거절한다.
상품 단위 직렬화이므로 서로 다른 상품의 댓글 쓰기는 같은 행을 잠그지 않는다.
기존 상품 상세 응답과 목록 API는 변경하지 않는다. 댓글 알림·실시간 갱신은 이번 범위에 포함하지 않는다.
