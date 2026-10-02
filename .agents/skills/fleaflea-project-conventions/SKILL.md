---
name: fleaflea-project-conventions
description: Fleaflea 백엔드 전반을 설계, 구현, 수정, 리뷰할 때 프로젝트 구조, 명명, API와 DTO, 도메인과 JPA, 예외, 트랜잭션, DB 마이그레이션, 테스트 규칙을 적용한다.
---

# Fleaflea Project Conventions

Fleaflea의 현재 계약과 코드 구조를 보존하면서 프로젝트 전반의 변경을 일관되게 만드는 저장소 전용 규칙이다.

## 작업 전 확인

1. 작업 대상 도메인의 `controller`, `service`, `repository`, `domain`, `dto`, `exception`과 연결된 다른 도메인을 확인한다.
2. 관련 테스트와 `src/main/resources/db/migration`의 현재 스키마를 확인한다.
3. [프로젝트 컨벤션](references/project-conventions.md)을 끝까지 읽고 작업에 적용한다.
4. 기존 공개 API나 저장 데이터의 계약과 새 규칙이 충돌하면 조용히 바꾸지 않는다. 호환성 영향과 변경 범위를 먼저 확인하고 요청된 범위 안에서만 변경한다.

## 핵심 원칙

- `domain/{도메인}` 중심 패키지 구조를 유지하고, 여러 도메인에서 실제로 공유하는 기능만 `global`에 둔다.
- Controller는 HTTP 계약, Service는 유스케이스와 트랜잭션, Entity는 불변식과 상태 전이, Repository는 영속성과 조회를 담당한다.
- Request/Response DTO로 외부 계약을 분리하고 Entity를 API 응답으로 직접 노출하지 않는다.
- 입력 형식은 DTO, 권한·존재 여부와 유스케이스 흐름은 Service, 상태 전이 불변식은 Entity와 DB 제약에 배치한다.
- 스키마 변경은 Flyway 마이그레이션으로 관리하고 JPA 모델, 제약 조건, 인덱스와 테스트를 함께 맞춘다.
- 기존 코드 전체를 새 규칙에 맞추는 일괄 리팩터링은 하지 않는다. 현재 작업과 직접 연결된 코드만 정리한다.

## 완료 전 확인

- API, Entity, Flyway 스키마와 테스트가 같은 계약을 표현하는가?
- 인증 주체, 소유권, 참여 권한과 상태 전이 실패가 검증되는가?
- 동시 요청이 가능한 변경은 잠금이나 DB 제약까지 고려했는가?
- 이미지 저장 키 같은 내부 값과 불필요한 개인정보가 응답에 노출되지 않는가?
- 변경 위험에 맞는 단위·통합·동시성 테스트를 실행했는가?
- 검증하지 못한 항목이나 환경 의존 단계가 결과에 명시되어 있는가?
