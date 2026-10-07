# 담당 코드 재점검

## 범위

기존 채팅 PR #157, 담당 도메인 정리 PR #159, QueryProjection 통일 PR #161의 변경 이력과 현재 채팅 폴더를 기준으로 확인했습니다. 검토 대상은 아래 파일 목록에 기록했습니다.

## 반영한 내용

- 요청 DTO와 거래 엔티티의 `request`, `body`를 실제 타입과 용도가 드러나는 변수명으로 변경했습니다.
- 도감 거래 컨트롤러의 `service`, `id`를 `collectionTradeService`, `collectionTradeRequestId`로 변경했습니다. 기존 URL의 `{id}`는 명시적인 `@PathVariable("id")`로 유지했습니다.
- 여러 매개변수를 한 줄에 몰아 쓰지 않도록 정리하고, 컨트롤러 매개변수 사이에 빈 줄을 추가했습니다. `@Valid`와 요청 바인딩 어노테이션 다음 줄에 DTO 타입을 둡니다.
- 거래 이력 응답과 교환 제안 물건의 직접 생성 코드를 응답 DTO의 `from(...)`으로 이동했습니다. 권한 검증과 이미지 URL·완료 시각 조회는 서비스에 남깁니다.
- 거래 이력 DTO에 필드 설명을 추가하고, 조회·권한 검증·QueryDSL 조건 생성 헬퍼 이름을 구체화했습니다.
- 변경 범위의 와일드카드 import를 구체적인 import로 변경했습니다.
- 채팅 메시지 요청 fixture 이름을 `createChatMessageSendRequest`로 변경하고 모든 호출부를 맞췄습니다.
- 파라미터화 테스트에 케이스 이름을 추가하고, 이미지 테스트의 예외 검증은 AssertJ로 통일했습니다.
- S3 이미지 변경·삭제의 반복된 트랜잭션 검증은 기존 `validateWritableTransaction()`을 재사용합니다.
- 소켓 인증 제한 시간의 숫자를 의미 있는 상수로 분리했습니다.

## 함께 확인한 기준

- 확인 범위에서 `var`, `@Positive`, `@NotNull(message=...)`, `saveAndFlush`, 직접적인 `new BusinessException(...)` 사용을 추가하지 않았습니다. 도메인별 기존 예외를 유지합니다.
- 요청 DTO는 입력을 받는 타입이므로 변환할 원본이 없는 곳에 `from`을 추가하지 않았습니다. QueryDSL projection은 `@QueryProjection` 생성자 방식을 유지합니다.
- 엔티티 감사 시각은 기존 BaseTimeEntity/BaseCreatedTimeEntity와 공통 감사 설정을 사용합니다. 응답 DTO의 `createdAt`은 응답 필드로 유지합니다.
- DB 제약·잠금, 친구 관계·참여자 권한, 메시지 중복 처리, 입력 상태의 비영속 전달 정책을 유지합니다.
- 테스트의 fixture 사용, 한국어 DisplayName, 커스텀 예외 검증을 확인했습니다. JSON 검증 테스트의 원시 문자열은 누락·null·잘못된 JSON을 검증하기 위해 유지합니다.

## 검증

`gradlew build --no-daemon` 성공. 전체 테스트 287개 중 285개 통과, 실제 S3 연동 테스트 2개는 기존 환경 변수 조건에 따라 건너뛰었습니다. 예외 검증 API의 최종 정리 후 이미지 단위·연동 테스트를 별도로 재실행해 통과했습니다(실제 S3 연동은 동일한 조건으로 건너뜀).

## 검토 파일

총 123개 파일을 범위에 포함했습니다.

- `build.gradle`
- `deploy/ec2/README.md`
- `deploy/ec2/nginx-chat.conf`
- `docs/friend-chat.md`
- `src/main/java/com/anabada/fleaflea/domain/begrequest/domain/BegRequest.java`
- `src/main/java/com/anabada/fleaflea/domain/begrequest/repository/BegRequestRepository.java`
- `src/main/java/com/anabada/fleaflea/domain/begrequest/service/BegRequestService.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/config/ChatWebSocketConfig.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/controller/ChatController.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/controller/ChatSocketController.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/domain/ChatMessage.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/domain/ChatRoom.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/dto/ChatMessageResponse.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/dto/ChatMessageSendRequest.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/dto/ChatReadRequest.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/dto/ChatReadResponse.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/dto/ChatRoomCreateRequest.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/dto/ChatRoomListResponse.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/dto/ChatRoomResponse.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/dto/ChatSocketEventResponse.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/dto/ChatTypingRequest.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/dto/ChatTypingResponse.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/event/ChatEvent.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/event/ChatEventListener.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/exception/ChatDuplicateMessageConflictException.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/exception/ChatFriendRequiredException.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/exception/ChatMessageNotFoundException.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/exception/ChatNotParticipantException.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/exception/ChatRateLimitExceededException.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/exception/ChatRoomNotFoundException.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/exception/InvalidChatMessageCursorException.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/repository/ChatMessageRepository.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/repository/ChatRoomRepository.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/security/ChatWebSocketAuthInterceptor.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/security/ChatWebSocketSessionDecorator.java`
- `src/main/java/com/anabada/fleaflea/domain/chat/service/ChatService.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/controller/CollectionItemController.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/domain/CollectionItem.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/domain/CollectionItemStatus.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/dto/CollectionItemCreateRequest.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/dto/CollectionItemResponse.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/dto/CollectionItemSummaryResponse.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/dto/CollectionItemUpdateRequest.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/exception/CollectionItemNotOwnerException.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/exception/CollectionItemTradeInProgressException.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/repository/CollectionItemRepository.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/repository/CollectionItemRepositoryCustom.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/repository/CollectionItemRepositoryImpl.java`
- `src/main/java/com/anabada/fleaflea/domain/collectionitem/service/CollectionItemService.java`
- `src/main/java/com/anabada/fleaflea/domain/friendship/controller/FriendshipController.java`
- `src/main/java/com/anabada/fleaflea/domain/friendship/repository/FriendshipRepository.java`
- `src/main/java/com/anabada/fleaflea/domain/friendship/service/FriendshipService.java`
- `src/main/java/com/anabada/fleaflea/domain/item/domain/Item.java`
- `src/main/java/com/anabada/fleaflea/domain/item/service/ItemService.java`
- `src/main/java/com/anabada/fleaflea/domain/market/controller/MarketController.java`
- `src/main/java/com/anabada/fleaflea/domain/market/dto/MarketInvitationResponse.java`
- `src/main/java/com/anabada/fleaflea/domain/market/dto/MarketSummaryProjection.java`
- `src/main/java/com/anabada/fleaflea/domain/market/exception/InvalidMarketScopeException.java`
- `src/main/java/com/anabada/fleaflea/domain/market/exception/MarketHostCannotLeaveException.java`
- `src/main/java/com/anabada/fleaflea/domain/market/exception/MarketHostOnlyException.java`
- `src/main/java/com/anabada/fleaflea/domain/market/exception/MarketMembershipNotFoundException.java`
- `src/main/java/com/anabada/fleaflea/domain/market/service/MarketQueryService.java`
- `src/main/java/com/anabada/fleaflea/domain/market/service/MarketService.java`
- `src/main/java/com/anabada/fleaflea/domain/marketmember/repository/MarketMemberRepositoryImpl.java`
- `src/main/java/com/anabada/fleaflea/domain/poke/controller/MemberPokeController.java`
- `src/main/java/com/anabada/fleaflea/domain/poke/exception/PokeAccessDeniedException.java`
- `src/main/java/com/anabada/fleaflea/domain/poke/exception/PokeNotFoundException.java`
- `src/main/java/com/anabada/fleaflea/domain/poke/exception/PokeSelfRequestException.java`
- `src/main/java/com/anabada/fleaflea/domain/poke/service/MemberPokeService.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/controller/CollectionTradeController.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/controller/TradeRequestListController.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/domain/CollectionTradeRequest.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/dto/TradeRequestHistoryDetailResponse.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/dto/TradeRequestListResponse.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/exception/CollectionTradeAccessDeniedException.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/exception/CollectionTradeDuplicateRequestException.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/exception/CollectionTradeInvalidOfferException.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/exception/CollectionTradeInvalidStatusException.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/exception/CollectionTradeOfferRequiredException.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/exception/CollectionTradeOwnershipChangedException.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/exception/CollectionTradeRequestNotFoundException.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/exception/CollectionTradeSelfRequestException.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/exception/InvalidTradeRequestDirectionException.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/exception/InvalidTradeRequestTypeException.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/exception/TradeRequestAccessDeniedException.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/repository/CollectionTradeRequestRepository.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/service/CollectionTradeService.java`
- `src/main/java/com/anabada/fleaflea/domain/trade/service/TradeRequestListService.java`
- `src/main/java/com/anabada/fleaflea/global/config/JpaAuditingConfig.java`
- `src/main/java/com/anabada/fleaflea/global/config/SecurityConfig.java`
- `src/main/java/com/anabada/fleaflea/global/dto/CursorPageResponse.java`
- `src/main/java/com/anabada/fleaflea/global/image/ImageService.java`
- `src/main/java/com/anabada/fleaflea/global/security/JwtTokenProvider.java`
- `src/main/resources/db/migration/V19__prevent_duplicate_active_friendships.sql`
- `src/test/java/com/anabada/fleaflea/domain/chat/ChatConcurrencyTest.java`
- `src/test/java/com/anabada/fleaflea/domain/chat/ChatEventIntegrationTest.java`
- `src/test/java/com/anabada/fleaflea/domain/chat/ChatMigrationTest.java`
- `src/test/java/com/anabada/fleaflea/domain/chat/ChatServiceIntegrationTest.java`
- `src/test/java/com/anabada/fleaflea/domain/chat/ChatWebSocketIntegrationTest.java`
- `src/test/java/com/anabada/fleaflea/domain/chat/controller/ChatControllerTest.java`
- `src/test/java/com/anabada/fleaflea/domain/chat/domain/ChatRoomTest.java`
- `src/test/java/com/anabada/fleaflea/domain/chat/event/ChatEventListenerTest.java`
- `src/test/java/com/anabada/fleaflea/domain/chat/repository/ChatRepositoryTest.java`
- `src/test/java/com/anabada/fleaflea/domain/chat/security/ChatWebSocketAuthInterceptorTest.java`
- `src/test/java/com/anabada/fleaflea/domain/chat/security/ChatWebSocketSessionDecoratorTest.java`
- `src/test/java/com/anabada/fleaflea/domain/chat/service/ChatServiceTest.java`
- `src/test/java/com/anabada/fleaflea/domain/collectionitem/controller/CollectionItemControllerTest.java`
- `src/test/java/com/anabada/fleaflea/domain/collectionitem/service/CollectionItemConcurrencyTest.java`
- `src/test/java/com/anabada/fleaflea/domain/collectionitem/service/CollectionItemIntegrationTest.java`
- `src/test/java/com/anabada/fleaflea/domain/collectionitem/service/CollectionItemServiceTest.java`
- `src/test/java/com/anabada/fleaflea/domain/friendship/repository/FriendshipMigrationTest.java`
- `src/test/java/com/anabada/fleaflea/domain/friendship/service/FriendshipConcurrencyTest.java`
- `src/test/java/com/anabada/fleaflea/domain/market/service/MarketQueryServiceTest.java`
- `src/test/java/com/anabada/fleaflea/domain/poke/service/MemberPokeServiceTest.java`
- `src/test/java/com/anabada/fleaflea/domain/query/CollectionMarketQueryRepositoryTest.java`
- `src/test/java/com/anabada/fleaflea/domain/trade/service/CollectionTradeServiceTest.java`
- `src/test/java/com/anabada/fleaflea/domain/trade/service/TradeRequestHistoryDetailServiceTest.java`
- `src/test/java/com/anabada/fleaflea/fixture/ChatFixture.java`
- `src/test/java/com/anabada/fleaflea/fixture/CollectionItemFixture.java`
- `src/test/java/com/anabada/fleaflea/fixture/CollectionTradeFixture.java`
- `src/test/java/com/anabada/fleaflea/fixture/FriendshipFixture.java`
- `src/test/java/com/anabada/fleaflea/global/image/ImageServiceIntegrationTest.java`
- `src/test/java/com/anabada/fleaflea/global/image/ImageServiceTest.java`
