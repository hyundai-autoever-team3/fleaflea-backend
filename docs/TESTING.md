# Fleaflea 테스트 작성 가이드

이 문서는 Fleaflea 백엔드에서 새 테스트를 작성할 때 사용할 기준과 예시를 정리합니다.

핵심 원칙은 간단합니다.

> 가장 작은 범위의 테스트로 검증하되, PostgreSQL의 실제 동작이 필요한 기능은 Testcontainers의 PostgreSQL로 검증합니다.

## 1. 시작하기

### 준비 사항

- JDK 25
- Docker가 실행 중인 환경

PostgreSQL 통합 테스트는 Docker를 통해 `postgres:16` 컨테이너를 자동으로 시작합니다. 로컬에 PostgreSQL을 별도로 설치하거나 5432 포트를 열 필요는 없습니다.

Docker 상태는 다음 명령으로 확인할 수 있습니다.

```bash
docker info
```

전체 테스트는 다음과 같이 실행합니다.

```bash
./gradlew test
```

처음 실행할 때는 PostgreSQL 이미지를 내려받느라 시간이 더 걸릴 수 있습니다. 이후 실행부터는 로컬 이미지 캐시를 사용합니다.

## 2. 어떤 테스트를 작성할지 선택하기

| 검증 대상 | 권장 테스트 | Spring 컨텍스트 | PostgreSQL |
| --- | --- | --- | --- |
| 엔티티 상태 변경, 값 객체, 계산 로직 | 순수 단위 테스트 | 사용하지 않음 | 사용하지 않음 |
| 서비스의 분기, 예외, 저장소 호출 순서 | Mockito 단위 테스트 | 사용하지 않음 | 사용하지 않음 |
| JPA 매핑, QueryDSL, Repository 쿼리 | `@DataJpaTest` | JPA 슬라이스만 사용 | Testcontainers 사용 |
| 트랜잭션, 이벤트, 여러 Repository를 묶은 서비스 | `@PostgresIntegrationTest` | 전체 컨텍스트 사용 | Testcontainers 사용 |
| 잠금, 중복 생성 방지, 동시 상태 전이 | 동시성 통합 테스트 | 전체 컨텍스트 사용 | Testcontainers 사용 |
| 실제 S3 연결과 업로드·삭제 | 외부 연동 테스트 | 필요한 객체만 구성 | 사용하지 않음 |

다음 질문으로 범위를 정하면 됩니다.

1. Spring이나 DB 없이 결과를 검증할 수 있는가?
   - 가능하면 단위 테스트를 작성합니다.
2. PostgreSQL의 제약 조건, 쿼리, 잠금, 트랜잭션에 결과가 의존하는가?
   - 의존하면 PostgreSQL 통합 테스트를 작성합니다.
3. 팀 외부의 실제 인프라가 필요한가?
   - 필요하면 기본 테스트와 분리한 opt-in 테스트로 작성합니다.

`@SpringBootTest`를 기본 선택으로 사용하지 않습니다. 컨텍스트가 클수록 테스트가 느려지고 실패 원인을 찾기 어려워집니다.

## 3. 공통 작성 규칙

### 이름과 구조

- 테스트 클래스 이름은 기본적으로 `{대상클래스}Test`를 사용합니다.
- 모든 `@Test`, `@ParameterizedTest`, `@RepeatedTest` 메서드에 `@DisplayName`을 작성합니다.
- 메서드 이름은 영어로 작성하고 검증하는 행동과 결과가 드러나게 합니다.
- `@DisplayName`은 자연스러운 한국어로 조건과 기대 결과가 함께 드러나게 작성합니다.
- `성공 테스트`, `예외 테스트`처럼 대상과 결과를 알 수 없는 표현은 사용하지 않습니다.
- 테스트 클래스의 `@DisplayName`은 선택 사항이지만, `@Nested`로 시나리오를 묶을 때는 중첩 클래스에도 `@DisplayName`을 작성합니다.
- 파라미터화 테스트는 `@ParameterizedTest(name = "...")`로 각 실행 케이스도 구분합니다.
- 테스트 본문은 준비(Arrange), 실행(Act), 검증(Assert)의 흐름을 유지합니다.
- 테스트 순서에 의존하지 않으며, 각 테스트는 단독 실행해도 성공해야 합니다.

새로 작성하거나 수정하는 테스트에는 이 규칙을 필수로 적용합니다. 아직 `@DisplayName`이 없는 기존 테스트는 관련 기능을 수정할 때 함께 보완합니다.

예시:

```java
@Test
@DisplayName("소유자가 아닌 회원은 상품을 수정할 수 없다")
void updateItem_rejectsNonOwner() {
    // given
    // when
    // then
}
```

### 검증 범위

성공 경로만 확인하지 말고 대상 기능에 해당하는 실패 경로도 함께 확인합니다.

- 인증 사용자 또는 principal
- 소유권과 참여 여부
- 존재하지 않는 리소스
- 허용되지 않는 상태 전이
- 빈 값, 최소·최대 길이, 날짜 경계
- 중복 요청과 동시 요청

AssertJ를 기본으로 사용합니다.

```java
assertThat(result.status()).isEqualTo(ItemStatus.AVAILABLE);

assertThatThrownBy(() -> service.update(itemId, otherMemberId, request))
        .isInstanceOf(BusinessException.class);
```

Mockito의 `verify`는 반환값이나 최종 상태만으로 확인할 수 없는 중요한 협력 객체 호출에만 사용합니다.

## 4. 단위 테스트

### 순수 단위 테스트

엔티티의 상태 변경이나 외부 의존성이 없는 로직은 Spring을 시작하지 않고 대상 객체를 직접 생성합니다.

```java
class ItemTest {

    @Test
    @DisplayName("상품을 완료하면 상태가 COMPLETED로 변경된다")
    void complete_changesStatusToCompleted() {
        Item item = createAvailableItem();

        item.complete();

        assertThat(item.getStatus()).isEqualTo(ItemStatus.COMPLETED);
    }
}
```

### Mockito 서비스 테스트

서비스의 권한 검사, 분기, 예외, Repository 호출을 검증하되 DB 의미가 필요하지 않을 때 사용합니다.

```java
@ExtendWith(MockitoExtension.class)
class SomeServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private SomeService service;

    @Test
    @DisplayName("서비스는 상품을 조회하여 결과를 반환한다")
    void execute_returnsExpectedResult() {
        Member member = MemberFixture.createMember(1L);
        Item item = ItemFixture.createItem(/* 필요한 인자 */);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        var result = service.execute(10L, member.getMemberId());

        assertThat(result).isNotNull();
        verify(itemRepository).findById(10L);
    }
}
```

단위 테스트에서는 `MemberFixture.createMember(Long)`처럼 ID가 주입된 객체를 사용할 수 있습니다. 그러나 이 방식으로 만든 엔티티를 실제 DB에 저장해서는 안 됩니다.

## 5. PostgreSQL 통합 테스트

### 전체 서비스 통합 테스트

트랜잭션 경계, Flyway 스키마, 여러 Repository의 협력, DB 제약 조건을 검증할 때 `@PostgresIntegrationTest`를 사용합니다.

```java
@PostgresIntegrationTest
@Transactional
class SomeServiceIntegrationTest {

    @Autowired
    private SomeService service;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("서비스 실행 결과가 데이터베이스에 저장된다")
    void execute_persistsExpectedState() {
        Member member = memberRepository.save(
                MemberFixture.createMember("member")
        );

        service.execute(member.getMemberId());

        // 저장 결과와 상태를 검증한다.
    }
}
```

`@PostgresIntegrationTest`에는 다음 구성이 포함되어 있습니다.

- `@SpringBootTest`
- `@ActiveProfiles("test")`
- PostgreSQL Testcontainer 설정 import
능
일반 통합 테스트에는 `@Transactional`을 붙여 각 테스트가 끝날 때 데이터를 롤백합니다. 실제 DB에 저장할 픽스처는 `MemberFixture.createMember("prefix")`처럼 매번 유일한 값을 만드는 팩토리를 사용합니다.

### Repository 슬라이스 테스트

JPA 매핑이나 QueryDSL 쿼리만 검증할 때는 전체 애플리케이션 대신 JPA 슬라이스를 사용합니다.

```java
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        QueryDslConfig.class,
        JpaAuditingConfig.class,
        PostgresTestContainerConfiguration.class
})
class SomeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SomeRepository repository;

    @Test
    @DisplayName("조회 조건을 모두 적용한 결과를 반환한다")
    void findSomething_appliesAllConditions() {
        // 데이터 저장
        entityManager.flush();
        entityManager.clear();

        var result = repository.findSomething();

        assertThat(result).isNotEmpty();
    }
}
```

`flush()`와 `clear()`를 호출한 뒤 조회하면 영속성 컨텍스트의 1차 캐시가 결과를 가리는 것을 막고 실제 SQL 조회 결과를 검증할 수 있습니다.

DB 제약 조건 위반을 검증할 때도 `save()`에서 끝내지 말고 `flush()`까지 호출해야 예외가 확실히 발생합니다.

### 스키마 기준

- Flyway 마이그레이션이 테스트 DB 스키마의 기준입니다.
- 테스트 프로필은 `spring.jpa.hibernate.ddl-auto=validate`를 사용합니다.
- 테스트를 통과시키기 위해 `create`, `create-drop`으로 바꾸거나 Flyway를 끄지 않습니다.
- H2를 PostgreSQL 쿼리, 인덱스, 제약 조건, 잠금 검증의 최종 근거로 사용하지 않습니다.

## 6. 동시성 테스트

동시성 테스트는 실제로 두 개 이상의 스레드와 서로 독립된 트랜잭션에서 요청을 실행해야 합니다.

```java
@PostgresIntegrationTest
@Sql(
        statements = "TRUNCATE TABLE members RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        statements = "TRUNCATE TABLE members RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class SomeConcurrencyTest {

    @Test
    @DisplayName("같은 요청을 동시에 처리하면 하나만 성공한다")
    void execute_concurrently_allowsOnlyOneSuccess() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(
                    () -> executeTask(ready, start)
            );
            Future<Boolean> second = executor.submit(
                    () -> executeTask(ready, start)
            );

            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }

        // 최종 DB 상태도 함께 검증한다.
    }
}
```

작성 시 다음을 지킵니다.

- 테스트 클래스에 `@Transactional`을 붙이지 않습니다. 테스트 트랜잭션에 데이터가 갇히면 다른 스레드에서 보이지 않을 수 있습니다.
- 두 작업이 준비된 뒤 최대한 동시에 시작하도록 `CountDownLatch`를 사용합니다.
- 성공 개수뿐 아니라 엔티티 상태, 생성된 행의 개수 등 최종 DB 상태도 검증합니다.
- 롤백에 의존하지 말고 `@Sql` 등으로 테스트 전후 데이터를 명시적으로 정리합니다.
- 예외를 무조건 삼키지 말고, 성공/실패 결과와 예상 가능한 실패인지 확인할 수 있게 구성합니다.

프로세스 내부 자료구조의 동시성만 확인하는 경우에는 Spring과 PostgreSQL 없이 순수 단위 테스트로 작성할 수 있습니다.

## 7. 픽스처 작성

공통 엔티티 생성은 `src/test/java/com/anabada/fleaflea/fixture`의 픽스처를 재사용합니다.

- 메서드 이름은 생성되는 상태를 드러냅니다.
- DB에 저장되는 유니크 필드는 UUID 등으로 매번 다른 값을 만듭니다.
- 테스트가 중요하게 보는 값은 픽스처 내부에 숨기지 말고 인자로 받습니다.
- 상태 전이는 리플렉션보다 실제 도메인 메서드를 우선합니다.
- 여러 테스트에서 반복될 때만 공통 픽스처로 올립니다.

```java
Member seller = MemberFixture.createMember("seller");
Market market = MarketFixture.createMarket(seller);
Item item = ItemFixture.createItem(market, seller, "테스트 상품");
```

## 8. 외부 인프라 테스트

실제 S3 테스트는 비용, 자격 증명, 네트워크에 의존하므로 기본 테스트 실행에서 제외합니다.

```java
@EnabledIfEnvironmentVariable(
        named = "S3_INTEGRATION_TEST",
        matches = "true"
)
class S3ConnectionTest {
    // 실제 S3 검증
}
```

명시적으로 실행할 때만 환경 변수를 설정합니다.

```bash
S3_INTEGRATION_TEST=true \
S3_BUCKET=<test-bucket> \
AWS_REGION=ap-northeast-2 \
./gradlew test --tests '*S3ConnectionTest'
```

- 운영 버킷 대신 테스트 전용 버킷을 사용합니다.
- 테스트가 생성한 객체는 `finally`에서 반드시 삭제합니다.
- 자격 증명이나 실제 버킷 이름을 코드와 문서에 넣지 않습니다.
- 기본 CI에서 이 테스트가 건너뛰어지는 것은 정상입니다.

## 9. 실행 명령

변경과 가장 가까운 테스트부터 실행한 뒤 범위를 넓힙니다.

```bash
# 테스트 클래스 하나
./gradlew test --tests \
  'com.anabada.fleaflea.domain.market.service.MarketQueryServiceTest'

# 테스트 메서드 하나
./gradlew test --tests \
  'com.anabada.fleaflea.domain.market.service.MarketQueryServiceTest.returnsRelationshipStatusForEachMarketMember'

# 전체 테스트
./gradlew test

# CI와 같은 빌드 검증
./gradlew build --no-daemon
```

GitHub Actions에서는 Gradle 테스트가 Testcontainers를 통해 PostgreSQL을 직접 시작합니다. 따라서 워크플로에서 별도의 PostgreSQL 서비스 컨테이너나 `DB_URL`을 중복 설정하지 않습니다.

## 10. 문제 해결

### PostgreSQL 컨테이너가 시작되지 않음

1. Docker Desktop 또는 Docker daemon이 실행 중인지 확인합니다.
2. `docker info`가 성공하는지 확인합니다.
3. 첫 실행이면 `postgres:16` 이미지 다운로드가 끝날 때까지 기다립니다.
4. CI에서는 실행 환경이 Docker를 사용할 수 있는지 확인합니다.

### Flyway 또는 `ddl-auto=validate`에서 실패함

엔티티와 마이그레이션의 불일치를 수정합니다. 테스트에서 스키마 자동 생성을 켜서 우회하지 않습니다.

### Repository 테스트가 내장 DB를 찾음

다음 세 가지가 모두 있는지 확인합니다.

- `@AutoConfigureTestDatabase(replace = Replace.NONE)`
- `PostgresTestContainerConfiguration` import
- `@ActiveProfiles("test")`

### 로컬 5432 포트가 사용 중임

Testcontainers는 임의의 호스트 포트를 할당하므로 일반적으로 충돌하지 않습니다. 테스트용 `DB_URL`이나 고정 포트를 별도로 설정하지 않습니다.

### S3 테스트가 실행되지 않음

`S3_INTEGRATION_TEST=true`가 없으면 의도적으로 건너뜁니다. 기본 단위·PostgreSQL 통합 테스트의 실패가 아닙니다.

## 11. 제출 전 체크리스트

- [ ] 가장 작은 적절한 테스트 범위를 선택했다.
- [ ] 모든 테스트 메서드에 조건과 기대 결과가 드러나는 한국어 `@DisplayName`을 작성했다.
- [ ] 성공 경로와 중요한 실패 경로를 함께 검증했다.
- [ ] 테스트가 실행 순서나 다른 테스트 데이터에 의존하지 않는다.
- [ ] DB 테스트는 실제 PostgreSQL과 Flyway 스키마를 사용한다.
- [ ] 일반 통합 테스트는 롤백되며, 동시성 테스트는 데이터를 명시적으로 정리한다.
- [ ] 유니크 필드가 충돌하지 않는 픽스처를 사용했다.
- [ ] 외부 인프라 테스트는 opt-in이며 생성한 리소스를 삭제한다.
- [ ] 대상 테스트 실행 후 `./gradlew test` 또는 `./gradlew build --no-daemon`이 성공한다.

## 12. 관련 파일

- `build.gradle`: 테스트 의존성과 Gradle 테스트 환경 변수 설정
- `src/test/java/com/anabada/fleaflea/support/PostgresIntegrationTest.java`: 전체 PostgreSQL 통합 테스트용 조합 애노테이션
- `src/test/java/com/anabada/fleaflea/support/PostgresTestContainerConfiguration.java`: PostgreSQL 16 컨테이너 설정
- `src/test/resources/application-test.yaml`: 테스트 프로필 설정
- `.github/workflows/ci.yml`: CI 빌드·테스트 실행
