# Fleaflea 프로젝트 컨벤션

이 문서는 현재 Fleaflea 코드와 DB 마이그레이션에서 반복되는 구조를 프로젝트 전체 규칙으로 정리한다. 사용자 요청과 이미 공개된 API·DB 계약이 가장 우선이며, 규칙 적용을 이유로 요청 범위를 넓히지 않는다.

## 1. 변경 범위와 근거

- 구현 전에 대상 도메인뿐 아니라 호출하는 Controller, 연결된 도메인, 예외, 테스트와 Flyway 스키마를 함께 확인한다.
- API 필드, 상태 값, 관계, null 허용 여부는 추측하지 않고 현재 DTO, Entity, 쿼리와 마이그레이션으로 확인한다.
- 규칙에 맞지 않는 기존 코드를 발견해도 현재 작업과 무관하면 유지한다. 대규모 패키지 이동, 이름 변경, 응답 형식 변경은 별도 합의 없이 섞지 않는다.
- 새 구현은 가장 가까운 도메인의 검증된 패턴을 우선 따르되, 명백한 결함까지 복제하지 않는다.

## 2. 패키지와 계층

- 기본 경로는 `com.anabada.fleaflea.domain.{domain}`이며 필요한 계층을 `controller`, `service`, `repository`, `domain`, `dto`, `exception`으로 나눈다.
- 이벤트, 리스너, 알림 전송, SSE, rate limit처럼 도메인 안에서 독립 책임이 생길 때만 `event`, `listener`, `notifier`, `sse`, `ratelimit` 같은 하위 패키지를 추가한다.
- `global`에는 보안, 공통 예외 응답, 공통 페이지 응답, 이미지 저장, JPA 기반 클래스와 설정처럼 여러 도메인이 실제로 공유하는 기능만 둔다. 편의를 위한 포괄적인 `common`이나 `util` 패키지는 만들지 않는다.
- Controller가 Repository나 Entity 상태를 직접 다루지 않게 한다. Service가 유스케이스를 조율하고, Repository가 조회·저장을 담당한다.
- 도메인 Entity는 HTTP DTO를 참조하지 않는다. DTO 변환은 DTO의 정적 팩터리나 Service 경계에서 수행한다.

## 3. 이름과 언어

- 클래스는 역할이 드러나게 `{Domain}Controller`, `{Domain}Service`, `{Domain}Repository`, `{UseCase}Request`, `{View}Response`, `{Domain}SearchCondition`, `{View}Projection`으로 짓는다.
- 메서드는 `create`, `find`, `search`, `update`, `delete`, `accept`, `reject`, `cancel`, `confirm`처럼 결과나 상태 전이가 드러나는 동사를 사용한다. 의미가 불분명한 `process`, `handle`, `execute`는 프레임워크 콜백이나 실제 처리기 역할이 아니면 피한다.
- 식별자는 `id`만 쓰기보다 `memberId`, `marketId`, `itemId`, `tradeRequestId`처럼 대상을 포함한다.
- 정적 팩터리는 인자 수가 아니라 의미로 선택한다. Entity의 새 생성을 표현하면 `create`, 주된 원본 모델에서 변환하면 `from`, 여러 독립 값을 조합해 값 객체·응답·이벤트를 만들면 `of`를 우선한다.
- Java 식별자는 영어를 사용한다. 사용자에게 노출되는 검증 메시지, Swagger 설명과 테스트 `@DisplayName`은 현재 코드처럼 자연스러운 한국어를 사용한다.
- 주석은 코드가 하는 일을 반복하지 않고 트랜잭션, 쿼리, 인덱스, 보상 처리처럼 선택 이유가 코드만으로 드러나지 않을 때 작성한다.

## 4. Controller와 HTTP API

- 공개 API는 `/api/v1` 아래의 리소스 중심 URL을 유지한다. 상위 리소스가 접근 범위나 소유 관계를 결정할 때만 `/markets/{marketId}/items`처럼 중첩한다.
- 인증 회원은 `@AuthenticationPrincipal Long memberId`로 받고, 신뢰할 수 없는 Request body나 query parameter에서 현재 회원 ID를 받지 않는다.
- JSON 요청은 `@RequestBody`, 파일이 포함된 multipart 요청은 `@ModelAttribute`를 사용하며 DTO에 `@Valid`를 적용한다. Path와 query의 범위 제약도 Controller 경계에서 검증한다.
- 생성은 실제 응답 본문과 함께 `201`, 일반 조회·수정은 `200`, 응답 본문 없는 삭제는 `204`를 기본으로 하되 현재 엔드포인트 계약과 상태 전이 의미를 먼저 확인한다.
- Endpoint를 추가하거나 계약을 바꾸면 `@Operation`, `@ApiResponses`, DTO의 `@Schema` 설명과 예시를 실제 동작에 맞게 함께 수정한다.
- Entity를 반환하지 않고 전용 Response DTO를 반환한다. Controller는 응답 상태와 바인딩만 담당하고 비즈니스 분기를 넣지 않는다.

## 5. DTO와 외부 계약

- Request, Response, 검색 조건과 조회 Projection은 기본적으로 `record`를 사용한다. 프레임워크 요구나 가변 상태가 분명할 때만 클래스를 선택한다.
- Request에는 입력 형식과 크기를 검증하는 Bean Validation을 선언한다. 존재 여부, 소유권, 참여 여부와 현재 상태에 따른 허용 여부는 Service·Entity에서 검증한다.
- Response는 해당 화면이나 API 계약에 필요한 필드만 담는다. Entity 전체, S3 object key, 토큰 내부 정보나 불필요한 개인정보를 노출하지 않는다.
- 저장소의 이미지 key는 `ImageService`를 통해 외부 URL로 변환한 뒤 응답한다. key와 URL의 의미를 필드명에서 구분한다.
- 공개 필드명, enum 값, null 허용 여부, 페이지 구조를 바꿀 때는 호출자 호환성을 확인하고 관련 문서와 테스트를 함께 갱신한다.
- Entity 변환이 단순하면 Response의 `from` 또는 `of` 팩터리를 사용한다. URL 생성이나 여러 Repository 조회처럼 I/O가 필요한 조합은 DTO 안에 넣지 않는다.

## 6. Entity와 도메인 규칙

- JPA Entity는 `@Getter`와 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 기본으로 하고 공개 Setter를 두지 않는다.
- 생성자는 외부에서 직접 호출하기보다 private 생성자·Builder와 의미 있는 `create` 팩터리로 감싼다. 생성 기본 상태와 필수 불변식은 Entity 생성 경로에서 확정한다.
- 상태 변경은 `accept`, `reject`, `cancel`, `startTrade`, `completeTrade`, `markAsRead`처럼 의도를 드러내는 메서드로 수행한다. Service에서 필드를 직접 조립하거나 임의 Setter를 추가하지 않는다.
- 상태 전이 순서, 중복 처리 방지, 당사자 검증처럼 Entity 단독으로 판단 가능한 규칙은 Entity 안에 둔다. 여러 Repository나 외부 시스템이 필요한 판단은 Service가 조율한다.
- 연관관계는 기본적으로 `LAZY` 단방향을 사용한다. 양방향 관계나 cascade는 실제 탐색·생명주기 요구가 있을 때만 추가한다.
- Enum은 `EnumType.STRING`으로 저장하고 DB CHECK 제약이 있으면 새 값과 마이그레이션, 검증 테스트를 함께 변경한다.
- 생성·수정 시각이 필요한 Entity는 `BaseCreatedTimeEntity` 또는 `BaseTimeEntity`를 일관되게 사용한다.

## 7. Service, 트랜잭션과 외부 부수 효과

- Service는 회원·마켓·대상 Entity를 조회하고 권한을 확인한 뒤 도메인 메서드를 호출하여 하나의 유스케이스를 완성한다.
- 조회 중심 Service는 클래스에 `@Transactional(readOnly = true)`를 두고 쓰기 메서드에 `@Transactional`을 선언하는 현재 패턴을 우선한다. 클래스 구성이 다른 기존 Service는 작업 범위 안에서 가장 명확한 경계를 선택한다.
- 상태 전이와 그 결과로 생기는 거래 이력·알림 저장은 가능한 한 같은 DB 트랜잭션에서 처리한다.
- SSE 전송처럼 커밋된 데이터만 외부에 보여야 하는 동작은 `AFTER_COMMIT` 이벤트를 사용한다. 비동기 실행이 필요한 경우 실행기, 실패 처리와 테스트를 함께 확인한다.
- S3 업로드·삭제는 DB 트랜잭션이 롤백해도 자동 복구되지 않는다. 기존 `ImageService`의 교체·정리 흐름을 보존하고 업로드 이후 DB 실패, 교체 실패, 삭제 실패의 보상 경로를 고려한다.
- 같은 상품이나 거래 요청을 동시에 변경할 수 있는 유스케이스는 잠금 대상과 획득 순서를 명시적으로 검토한다. 비관적 잠금이나 DB unique 제약은 실제 경쟁 조건을 막을 때 사용하고 모든 조회에 관성적으로 추가하지 않는다.

## 8. Repository와 조회

- 단순 조회는 Spring Data 파생 쿼리를 사용한다. 동적 조건, Projection, 복잡한 정렬이나 count 최적화가 필요하면 `{Domain}RepositoryCustom`과 `{Domain}RepositoryImpl`의 QueryDSL 구조를 사용한다.
- 목록 API는 Entity 전체보다 필요한 필드의 Projection을 우선 검토하고, 연관 Entity 접근은 fetch join이나 `@EntityGraph`로 N+1을 방지한다.
- 페이지 정렬에는 `createdAt DESC, {domainId} DESC`처럼 유일한 보조 정렬 키를 두어 결과 순서를 안정적으로 유지한다.
- 검색 조건이 없을 때 불필요한 WHERE 절을 만들지 않는다. QueryDSL 조건 메서드는 값이 없으면 `null`을 반환해 조건에서 제외하는 현재 패턴을 따른다.
- 쿼리와 인덱스는 함께 설계한다. 새 인덱스는 실제 WHERE·ORDER BY 순서와 조회 빈도를 근거로 Flyway에 추가하고 관련 Repository 통합 테스트나 실행 계획으로 확인한다.

## 9. 예외와 오류 응답

- 예상 가능한 비즈니스 실패는 도메인별 구체 예외로 표현하고 `BusinessException`을 상속한다. 해당 예외는 중앙 `ErrorCode`의 상태·코드·메시지를 사용한다.
- Error code 문자열은 중복 없는 대문자 `SNAKE_CASE`로 유지한다. 새 오류는 `ErrorCode`, 도메인 예외 클래스, 발생 지점과 테스트를 한 변경으로 추가한다.
- API 오류 응답은 `ErrorResponse(code, message)` 형식을 유지한다. 임의의 응답 형식이나 Controller별 예외 처리를 만들지 않는다.
- 잘못된 입력은 400, 인증 실패는 401, 권한·소유권 실패는 403, 리소스 부재는 404, 현재 상태나 무결성 충돌은 409를 기본으로 하되 기존 계약과 의미를 확인한다.
- DB unique, FK, NOT NULL과 CHECK 제약은 애플리케이션 검증의 마지막 방어선이다. 동시 요청에서 반드시 지켜야 할 규칙을 사전 조회만으로 보장하지 않는다.

## 10. Flyway와 데이터베이스

- PostgreSQL 스키마의 변경 이력은 `src/main/resources/db/migration`의 Flyway 파일이 권위다. 이미 적용 가능한 기존 migration을 수정하지 않고 다음 버전의 `V{n}__{description}.sql`을 추가한다.
- 애플리케이션 기본값인 `ddl-auto=validate`를 유지한다. Entity 변경을 Hibernate 자동 DDL에 맡기지 않는다.
- 컬럼의 이름·타입·길이·null 허용 여부, FK 삭제 정책, unique·CHECK 제약과 인덱스를 Entity와 함께 맞춘다.
- 데이터 보정이나 제약 강화는 기존 운영 데이터가 새 조건을 만족하는지 고려해 순서를 설계한다. 파괴적 변경은 명시적 요청과 이행 계획 없이 수행하지 않는다.
- H2 PostgreSQL mode 테스트는 빠른 호환성 검증으로 사용할 수 있지만 PostgreSQL 고유 SQL, 잠금, 인덱스와 실행 계획의 최종 증거로 취급하지 않는다.

## 11. 테스트와 검증

- 테스트는 JUnit 5와 AssertJ를 사용하고 테스트 이름 또는 한국어 `@DisplayName`으로 행위와 기대 결과가 드러나게 한다.
- 순수 분기와 Service 조율은 Mockito 단위 테스트를 사용한다. JPA 매핑, QueryDSL, Flyway 제약, 트랜잭션과 동시성은 `@DataJpaTest` 또는 `@SpringBootTest` 통합 테스트로 검증한다.
- 반복되는 Entity 준비는 `src/test/java/com/anabada/fleaflea/fixture`의 Fixture를 재사용하거나 같은 구조로 추가한다.
- 기능 변경에는 성공 경로뿐 아니라 인증 주체, 소유권·참여 권한, 리소스 부재, 잘못된 상태와 경계값을 포함한다.
- 거래 수락·완료, 횟수 제한처럼 경쟁 조건이 핵심이면 실제 병렬 요청과 독립 트랜잭션을 사용하는 동시성 테스트를 추가한다.
- 먼저 변경 영역의 표적 테스트를 실행하고, 완료 전 가능한 범위에서 `./gradlew test`로 전체 회귀를 확인한다. 환경 때문에 실행하지 못한 검증은 실패 원문과 범위를 결과에 남긴다.

## 12. 기술 기준, 설정과 보안

- 프로젝트의 Gradle Wrapper와 Java 25 toolchain, Spring Boot 4 계열을 기준으로 실행한다. 의존성이나 런타임 버전 변경은 기능 작업에 임의로 섞지 않고 호환성과 배포 환경을 별도로 확인한다.
- 환경별 값은 `application.yaml`의 환경 변수 placeholder로 주입한다. 새 설정을 추가하면 필요에 따라 `.env.example`, Compose, CI/CD와 운영 문서를 함께 갱신한다.
- 실제 비밀번호, JWT secret, AWS credential, 토큰과 개인 데이터는 코드·테스트 fixture·문서·로그에 남기지 않는다.
- 인증·인가 경로나 CORS를 바꾸면 `SecurityConfig`, JWT filter, 예외 응답과 보안 테스트를 함께 확인한다. 보호 API의 사용자 식별은 검증된 SecurityContext를 기준으로 한다.
- 배포와 운영 절차는 `deploy/ec2`, 성능 환경과 측정 절차는 `performance` 아래의 전용 문서를 따른다. 일반 기능 변경에서 운영 설정이나 측정 결과를 근거 없이 덮어쓰지 않는다.

## 13. 마무리 기준

- 코드 변경과 함께 필요한 Swagger, Flyway, 예외 코드와 테스트가 빠짐없이 갱신되었는지 확인한다.
- `git diff --check`로 공백 오류를 확인하고, `git status --short`에서 사용자 작업이나 무관한 파일이 섞이지 않았는지 확인한다.
- 빌드 성공을 API 런타임, PostgreSQL 동작, S3 접근 또는 동시성 검증으로 과장하지 않는다. 실제로 확인한 수준만 보고한다.
