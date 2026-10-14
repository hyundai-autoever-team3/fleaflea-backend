# 마켓·도감 Redisson 락

## 적용 범위
- 마켓: 수정·삭제·참여·탈퇴·초대 코드 재발급의 분산 락 범위를 유지하면서 `@DistributedLock`으로 이름을 정리하고 구현을 Redisson RLock으로 교체.
- 도감: 수정·삭제, 도감 거래 생성·수락·거절·취소·완료, 구걸 생성·수락·거절·취소·완료, 도감 아이템을 연결하는 상품 생성.
- 도감 신규 생성과 조회에는 기존 아이템의 락이 필요하지 않음. 일반 상품 거래·회원 탈퇴 전체를 Redis로 직렬화하는 변경은 포함하지 않음.
- 기존 PostgreSQL 비관적 락·DB 제약·권한 및 상태 검증 유지. Redis는 DB 정합성 보호를 대체하지 않음.

## 키와 순서
- `fleaflea:redisson:lock:market:{marketId}`
- `fleaflea:redisson:lock:collection-item:{collectionItemId}`
- 교환은 대상·제안 아이템 키를 중복 제거하고 문자열 오름차순으로 획득, 역순으로 해제. 요청 방향과 관계없이 동일한 순서를 사용.
- 키 집합 전체에 하나의 대기 예산 적용. 중간 획득 실패 시 이미 획득한 락도 해제.
- 거래/구걸 ID로부터 아이템 ID를 찾을 때는 불변 snapshot ID의 scalar 조회만 사용. 대기 전에 영속 엔티티를 로딩해 오래된 상태를 재사용하지 않음. 실제 권한·상태는 락 획득 후 기존 서비스에서 검증.
- 새로운 진입점을 추가할 때 같은 아이템 키를 사용하고, 이미 보유한 키보다 앞선 키를 추가로 획득하는 중첩 호출을 피할 것.

## 수명과 오류
- 락 Aspect는 트랜잭션 Advice 밖에서 실행. 서비스 트랜잭션이 끝난 뒤 해제.
- 외부 REQUIRED 트랜잭션이 이미 있으면 afterCompletion에서 커밋·롤백 후 해제.
- `tryLock(wait, unit)` 사용: 고정 lease를 지정하지 않고 Watchdog 자동 연장 활성화.
- `REDIS_LOCK_WAIT_TIME` 기본 5초. `REDIS_LOCK_LEASE_TIME`은 기존 환경변수 이름을 보존하며 Watchdog timeout으로 사용(기본 60초, 최소 3초).
- Redis host·port·username·password·database·SSL·연결/명령 타임아웃은 Spring Redis 설정을 재사용. 단일 Redis 구성만 지원.
- 획득 시간 초과는 기존 RESOURCE_BUSY(409), Redis 연결 실패·인터럽트는 REDIS_LOCK_UNAVAILABLE(503). 인터럽트 상태 복구.
- 해제 시 현재 스레드 소유권 확인. 소유권 상실·해제 실패는 로그에 기록하고 이미 커밋된 요청을 실패 응답으로 바꾸지 않음.
- 기존 redis.lock.acquire / redis.lock.hold 및 마켓 처리·DB 조회 시간 계측 유지.

## 배포와 공존
기존 RedisLockRegistry는 문자열, Redisson은 hash 형식으로 락을 보관하므로 기존 키 공간과 분리한다. Blue/Green 공존 중 서로 다른 버전의 Redis 락은 상호 배제하지 않는다. 기존부터 DB 비관적 락을 사용하던 마켓·도감 수정/삭제 경로는 유지한 DB 락이 공통 보호 경계이며 DB-only 경합도 회귀 검증한다. 구걸 상태 변경처럼 이전 버전에 같은 보호가 없는 경로까지 혼합 버전 안전성을 보장하지 않는다. 최초 전환은 관련 쓰기를 잠시 중지하고 기존 프로세스의 요청·트랜잭션을 drain한 뒤 새 버전으로 전환해야 한다. 이후 동일 Redisson 버전끼리의 Blue/Green은 공통 키를 사용한다. Redis 공통 연결, 채팅 Pub/Sub, Refresh Token은 기존 Spring Data Redis를 계속 사용한다.

## 로컬 검증
Java 25와 Docker 실행 후 `./gradlew test` 실행. Redis와 PostgreSQL 컨테이너를 사용하는 통합 테스트가 포함된다. 운영 서버 부하 시험과 #186·#187의 장시간 비교 시험은 이번 전환의 선행 조건이 아니다.

## 리뷰 반영과 DB 락 유지 근거

서비스에 사용하는 어노테이션은 `DistributedLock`, Aspect는 프로젝트의 기존 Aspect 명명 방식에 맞춘 `DistributedLockAspect`다. Redisson 연결 구현에 종속되는 설정만 `global/config/RedissonConfig`에 둔다. 예외 클래스도 `DistributedLockBusyException`, `DistributedLockUnavailableException`으로 맞춘다. 기존 락 키 문자열·환경변수·오류 코드·지표 이름은 호환성을 위해 유지한다.

현재 DB 비관적 락은 단순히 코드를 건드리지 않기 위해 남긴 것이 아니다. Redis 락을 사용하지 않는 기존 진입점과의 경합, 이전 버전과 공존하는 경로, Redis 프로세스 정지나 watchdog 소유권 상실 뒤 늦게 진행하는 요청까지 Redisson만으로 DB 쓰기 정합성을 보장하지 않으므로 기존 DB 보호를 유지한다. 단, 모든 경로가 같은 DB 락을 쓰는 것은 아니므로 이 조합이 전체 기능의 안전성을 자동으로 보장하는 것은 아니다. 각 쓰기 경로의 DB 제약·락 범위 검토가 함께 필요하다.

비관적 락 제거 또는 낙관적 락 전환은 보류한다. 후속 검토에서는 모든 쓰기 진입점의 공통 보호 경계, 버전 컬럼과 마이그레이션, 낙관적 충돌의 재시도·사용자 응답, Redis 장애·락 소유권 상실 경합을 확인한 뒤 비교한다. 낙관적 락을 추가하는 것만으로 모든 도메인의 교차 행 제약이 해결되는 것은 아니다.

공유된 컬리 글의 핵심인 트랜잭션 완료 후 락 해제 원칙은 현재 afterCompletion 방식으로 유지한다. 글의 고정 lease 또는 REQUIRES_NEW 예시를 그대로 도입하지 않고 기존 트랜잭션 경계를 보존한다.
참고: https://helloworld.kurly.com/blog/distributed-redisson-lock/
