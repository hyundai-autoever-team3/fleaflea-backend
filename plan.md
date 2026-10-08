# Fleaflea 로깅·모니터링 구현 계획

- 작성일: 2026-10-01
- 상태: 구현·핵심 로컬 검증 완료, 운영 적용·장애 훈련 전
- 목표: 장애 감지 → 영향 API 확인 → 로그 검색 → 요청 처리 구간 분석 → 복구 확인
- 범위: Spring Boot, Nginx, PostgreSQL, EC2·컨테이너, 이미지·거래·알림·채팅 SSE

## 1. 현재 상태와 설계 기준

저장소에서 확인한 구성은 다음과 같다. 실제 운영 서버의 사양·설정은 배포 준비 단계에서 확인한다.

| 항목 | 현재 상태 | 구현 방향 |
| --- | --- | --- |
| 런타임 | Spring Boot 4.1.1, Java 25 | 기존 버전 유지, 호환되는 계측 의존성 검증 |
| 배포 | 호스트 Nginx, Compose의 앱·PostgreSQL, GitHub Actions·SSM 배포 | 앱 배포와 모니터링 배포 분리 |
| 상태 검사 | 앱 포트 8080의 `/actuator/health`, Compose·배포 스크립트에서 사용 | 관리 포트 분리에 맞춰 앱 포트의 `/readyz`로 검사 경로를 함께 전환하고 외부 HTTP 검사 추가 |
| 지표 | Actuator 있음, 운영 노출은 health만 설정 | Prometheus registry와 내부 수집 경로 추가 |
| 로그 | 공통 예외·이미지·비동기 실패 로그 있음 | JSON 로그, traceId 연계, 중앙 검색 |
| 성능 측정 | 로컬 `-Pperf`, k6 시나리오·결과 문서 있음 | 운영 관측과 분리 유지, 전후 비교에 재사용 |
| 알림·채팅 | AFTER_COMMIT 이벤트, 공용 비동기 실행기, SSE | `@Async`와 문맥 전파, 실행기 기본 지표·거절 로그 |

`notificationSseExecutor`는 core 4, max 8, queue 1000을 유지한다. 알림 리스너, `ChatEventListener`, 주기적 SSE heartbeat는 `@Async`로 실행기를 공유한다. 포화 시 SSE 작업은 버리고 경고 로그를 남긴다. 알림과 채팅 데이터는 DB에 남으며 클라이언트의 재조회로 복구한다. 전달 보장·재시도 정책 변경은 별도 기능 개선으로 분리한다.

프로젝트 컨벤션에 따라 기존 API 응답, 인증·인가, 거래 트랜잭션, DB 스키마를 보존한다. 이번 작업에 무관한 리팩터링은 포함하지 않는다.

## 2. 목표 구성

```text
앱 EC2
  Spring Boot ── 내부 관리 포트의 지표 ─→ Alloy ── remote write ─→ Prometheus
       ├─ JSON stdout ────────────────→ Alloy ───────────────→ Loki
       └─ OTLP trace ─────────────────→ Alloy ───────────────→ Tempo
  Nginx·PostgreSQL 로그 ──────────────→ Alloy ───────────────→ Loki
  서버·DB exporter ───────────────────→ Alloy ── remote write ─→ Prometheus

모니터링 서버
  Prometheus + Loki + Tempo + Grafana
  Prometheus rules ─→ Alertmanager ─→ 팀 알림 채널

앱 EC2 외부의 HTTP 검사 ─→ 접속 실패·복구 알림
```

- 운영 기본안은 별도 모니터링 서버이다. 앱 EC2에는 Alloy와 필요한 exporter만 둔다.
- 앱의 8080 포트는 API와 `/readyz`를 제공하고, 별도 관리 포트 8081은 `/actuator/health`와 `/actuator/prometheus`를 제공한다. 앱 포트의 readiness 검사는 실제 API와 같은 웹 서버를 확인한다. readiness 그룹에 DB 상태를 포함해 기존 전체 health 검사에서 확인하던 DB 장애도 감지한다.
- 관리 포트를 분리하면 앱 포트 8080의 기존 `/actuator/health`는 사라진다. Compose·deploy.sh·운영 문서·외부 HTTP 검사를 같은 배포에서 `/readyz`로 전환하고, 기존 경로를 쓰는 외부 검사나 호출자가 없는지 먼저 확인한다. Nginx는 `/readyz`만 필요한 범위로 전달하고 관리 포트는 프록시하지 않는다.
- Spring Boot 설정의 목표는 `management.server.port=8081`, `management.endpoints.web.exposure.include=health,prometheus`, `management.endpoint.health.probes.enabled=true`, `management.endpoint.health.probes.add-additional-paths=true`, `management.endpoint.health.group.readiness.include=readinessState,db`이다. 실제 Boot 4.1.1 환경에서 `/readyz`와 DB 장애 응답을 확인한 뒤 확정한다.
- 앱 EC2의 Alloy가 같은 Compose 네트워크에서 관리 포트와 exporter를 수집하고 중앙 Prometheus의 remote-write 수신 경로로 전송한다. 중앙 서버가 앱 EC2의 Docker 서비스명이나 관리 포트에 직접 접근한다고 가정하지 않는다.
- 관리 포트와 exporter는 모니터링 서버에서만 접근하도록 보안 그룹·바인딩을 제한한다. 애플리케이션 보안 설정도 함께 적용한다.
- Loki·Tempo 수신 경로는 앱 수집기만 접근할 수 있게 제한한다. 신뢰할 수 없는 네트워크 구간은 TLS·인증을 적용한다.
- Grafana는 인증된 운영자에게만 제공한다. 서비스 API의 공개 Nginx 경로로 내부 지표·수집 포트를 노출하지 않는다.
- 외부 HTTP 검사는 앱 EC2와 독립적으로 실행한다. 모니터링 서버 자체의 중단도 외부 검사 또는 별도 생존 신호로 감지한다.
- 예산 때문에 동일 EC2를 사용해야 한다면 CPU·메모리·디스크 여유를 측정한 후 제한된 보관량으로 배치한다. 공동 장애 위험은 외부 검사로 보완한다.

### 구현 결과 (2026-10-01)

- 애플리케이션에 ECS JSON 로그, HTTP 완료 로그, Prometheus 지표, OTLP trace, 배포 버전 지표를 적용했다.
- API 포트 8080의 `/readyz`와 loopback으로 게시되는 관리 포트 8081을 분리했다. 로컬 PostgreSQL 중단 시 `/readyz`가 약 3초 안에 503을 반환하고 DB 복구 후 200으로 돌아오는 것을 확인했다.
- Alloy, Prometheus, Loki, Tempo, Grafana, Alertmanager, Blackbox Exporter와 호스트·컨테이너·PostgreSQL exporter 설정을 추가했다. 중앙 저장소는 named volume과 보관 제한을 사용한다.
- 로컬 종단 간 검증에서 앱 지표가 Prometheus에 저장되고, 앱 요청 trace가 Tempo에 저장되며, 동일 trace ID를 포함한 JSON 로그가 Loki에서 검색되는 것을 확인했다.
- 남은 작업은 운영 인프라 값 입력과 실제 배포이다. 모니터링 서버 사설 IP·보안 그룹, Grafana 주소·비밀번호, 전용 PostgreSQL 모니터링 계정, Discord 웹훅 비밀 파일, Nginx 예제 적용은 운영자가 확정해야 한다.

### 현재 구성 보충 (2026-10-08)

- Grafana는 **Fleaflea Overview**, **Fleaflea Host Details**(Node Exporter Full 1860 기반), **Fleaflea PostgreSQL Details**를 파일로 프로비저닝한다. 화면 제목·패널명·범례는 영어이며 Fleaflea에서 작성한 설명은 한국어다.
- Alertmanager의 `discord` receiver는 웹훅 비밀 파일을 읽고 발생·해제 알림을 보내도록 설정했다. 운영 서버의 비밀 파일 설치와 실제 Discord 수신 확인은 별도 운영 작업으로 남아 있다.
- 현재 실행·조회 절차는 [`docs/LOCAL_MONITORING.md`](docs/LOCAL_MONITORING.md)와 [`docs/MONITORING_VIEWING_GUIDE.md`](docs/MONITORING_VIEWING_GUIDE.md)를 기준으로 한다.

체크 표시는 저장소 구현과 로컬 검증 완료를 뜻한다. 운영 서버에서만 확인할 수 있는 항목은 체크하지 않는다.

## 3. 구현 전 확정할 운영 값

아래 값이 미정이어도 로컬 구현과 테스트는 진행할 수 있다. 운영 배포 전에 확정한다.

- [ ] 앱 EC2 사양, 메모리·디스크 여유, 평균·최대 트래픽 확인
- [ ] 모니터링 서버 사양·예산·사설 통신 경로 확정
- [ ] 알림 수신 채널·담당자·운영 시간 확정
- [ ] 실제 Nginx 설정과 `/actuator/health` 사용처 확인, `/readyz` 외부 검사 라우팅 확정
- [ ] 데이터 저장 위치·용량·보관 기간 확정
- [x] 배포할 도구 버전과 이미지 태그 고정

| 항목 | 초기 제안 | 조정 기준 |
| --- | --- | --- |
| 지표 수집 주기 | 15초 | 수집 부하와 필요한 감지 속도 |
| 로그 보관 | 7일 | 일일 발생량·장애 조사 기간 |
| 지표 보관 | 15일 | 추세 비교 기간·디스크 용량 |
| 트레이스 보관 | 3일 | span 발생량·샘플링 비율 |
| 트레이스 샘플링 | 검증 환경 100%, 운영 우선 10% | 실제 요청량과 추적 필요성 |

보관 기간만 설정하지 않고 용량 상한·디스크 알림·삭제 동작까지 검증한다. 운영 10% 샘플링에서는 오류 요청의 트레이스도 누락될 수 있다. 따라서 모든 오류는 샘플링과 무관하게 로그·지표로 조사 가능해야 하며, 오류 로그에서 항상 Tempo로 이동할 수 있다고 가정하지 않는다. 오류 우선 보관이 필요하면 후속 단계에서 tail sampling을 검토하며, 앱에서 이미 버린 트레이스를 수집기가 복구할 수 없다는 점을 반영한다.

## 4. 1단계: 애플리케이션 관측 기반

### 작업

- [x] `micrometer-registry-prometheus` 추가
- [x] Micrometer Tracing + OpenTelemetry bridge + OTLP exporter 구성
- [x] Boot 4.1.1의 의존성 관리와 실제 자동 설정을 확인해 버전·설정 키 확정
- [x] 초기 계측은 Micrometer 기반으로 통일하고, Java agent와 중복 HTTP span이 생기지 않게 구성
- [x] 로컬 개발용 읽기 쉬운 로그와 운영용 JSON 로그 설정 분리
- [x] 로그 공통 필드: 시각, 수준, 서비스, 환경, 배포 버전, traceId, spanId, 메시지
- [x] 요청 완료 로그: HTTP method, route template, status, durationMs, errorCode를 필요한 위치에서 기록
- [x] 예외 스택은 책임 있는 경계에서 한 번 기록하고, 예상 가능한 입력·비즈니스 실패는 서버 장애와 구분
- [x] 현재 비동기 예외 처리의 인자 전체 출력 제거 또는 안전한 허용 필드로 대체
- [x] 알림·채팅·heartbeat가 공유하는 `notificationSseExecutor`에 추적 문맥 전파를 적용하고, 작업 후 문맥을 복원·정리
- [x] 관리 포트 8081에 health·prometheus만 노출하고, 앱 포트 8080에는 health group의 `/readyz`를 추가
- [x] readiness 그룹에 `readinessState`와 `db`를 포함하는 설정을 검증하고, Compose·deploy.sh의 `8080/actuator/health` 검사를 `8080/readyz`로 같은 배포에서 전환
- [ ] Nginx의 `/readyz` 전달·기존 `/actuator/health` 사용처·외부 HTTP 검사도 전환하고, 8081 관리 포트가 외부 API 경로로 전달되지 않는지 확인
- [x] `SecurityConfig`에서 앱 포트의 `/readyz`만 익명 검사에 허용하고, 기존 JWT 필터와 관리 포트 보안 체인의 적용 범위를 검증
- [x] 배포 스크립트가 새 이미지의 `/readyz`를 검사하고, 실패 시 이전 이미지가 사용하던 health 경로로 롤백을 검사하도록 이전 경로를 배포 전에 기록
- [x] 관리 엔드포인트 보안 체인을 명시적으로 구성하고 일반 사용자 JWT로 지표를 읽지 못하게 검증
- [x] OTLP 전송을 비동기·제한된 큐·타임아웃으로 설정하고 수집기 장애가 업무 요청을 막지 않게 구성

### 데이터 기록 규칙

- 비밀번호, Authorization, 쿠키, JWT, 요청·응답 본문, 파일 내용, 초대 코드, SQL 바인딩 값은 수집하지 않는다.
- 쿼리 문자열·원본 URL·예외 메시지에도 민감 값이 들어갈 수 있으므로 앱·Nginx·exporter·span 속성을 함께 점검한다.
- 지표 라벨은 service, environment, route template, method, status, 제한된 outcome 등으로 한정한다.
- memberId, itemId, requestId, traceId, 원본 URL은 지표 라벨과 Loki 인덱스 라벨에 넣지 않는다.
- traceId·spanId는 로그 필드로 검색한다. 별도 requestId를 기본으로 중복 도입하지 않는다.
- 샘플링되지 않은 요청의 로그에도 traceId가 있을 수 있지만 Tempo에는 데이터가 없을 수 있음을 운영 문서에 명시한다.
- SSE는 일반 API 응답 시간 집계에서 분리하며, 연결 전체를 장시간 업무 span으로 유지하지 않는다.

### 완료 기준

- [x] 검증 요청의 JSON 로그와 HTTP trace가 같은 traceId로 연결된다.
- [x] 서로 다른 요청·재사용 스레드 사이에 추적 문맥이 섞이지 않는다.
- [x] 공개 사용자·일반 로그인 사용자는 prometheus 등 관리 정보를 읽을 수 없다.
- [x] Compose·배포 검사에서 앱 포트 8080의 `/readyz`가 정상이며 DB 장애를 감지한다. 관리 포트 8081의 health만 성공하는 상태를 정상으로 판정하지 않는다. 외부 검사는 운영 배포 후 확인한다.
- [x] 로컬 API·CORS·인증 동작을 확인했고 Compose에서 8081을 host loopback에만 게시했다. 운영 보안 그룹은 배포 시 재확인한다.
- [x] Alloy 또는 Tempo가 중단되어도 앱의 정상 요청은 처리된다.

## 5. 2단계: 수집·저장·조회 환경

### 작업

- [x] `deploy/monitoring/`에 모니터링 Compose와 도구별 설정 추가
- [x] Prometheus에서 HTTP 요청 수·5xx·응답 시간 histogram, JVM·GC, Hikari 지표 수집
- [ ] HTTP histogram의 bucket을 실제 응답 시간·목표에 맞게 설정하고 p95·p99를 계산
- [x] 호스트·컨테이너 exporter와 PostgreSQL exporter 구성, 전용 `pg_monitor` 계정 사용 절차 추가
- [x] Alloy로 앱·PostgreSQL 컨테이너 로그와 호스트 Nginx access/error 로그 수집
- [x] Nginx access 로그는 query·민감 경로를 제외하고 request 시간·upstream 시간·status 중심으로 구성
- [x] Nginx 로그 연결은 검증된 식별자를 사용할 수 있을 때 적용하고, 초기에는 시각·경로·상태 기반으로 조회
- [x] Alloy에서 OTLP를 받아 Tempo로 전달, 재시도·버퍼·드롭 지표와 로그 수집 위치 영속화
- [x] Loki·Tempo·Prometheus 영속 저장소, 보관·삭제 설정, 자원 제한 적용
- [x] Docker 로그 회전과 호스트 Nginx logrotate 적용
- [x] Docker socket 접근은 호스트 제어 권한에 준하는 위험으로 취급하고 제한된 읽기 전용 proxy 사용
- [x] Grafana 데이터 소스·대시보드·알림을 provisioning 파일로 관리
- [x] Loki의 traceId에서 Tempo로 이동하고, Tempo에서 해당 시간대·traceId 로그로 이동하도록 구성
- [x] 배포 SHA를 앱 환경·로그·trace resource·단일 build info 지표에 반영

### 완료 기준

- [ ] 앱·Nginx·DB 로그가 중앙에서 검색되고 JVM·서버·DB 지표가 조회된다.
- [x] 샘플링된 HTTP 요청 trace가 Tempo에 저장되고 같은 trace ID의 JSON 로그가 Loki에서 검색되며, Grafana 양방향 연결 설정이 적용된다.
- [x] 컨테이너 재시작 후 named volume의 관측 데이터와 provisioning 대시보드가 유지된다.
- [ ] 보관 기간 만료 데이터가 실제로 정리되고 수집 중단·드롭을 관측할 수 있다.

## 6. 3단계: 핵심 업무 흐름 계측

| 대상 | 추가할 span | 추가할 지표 |
| --- | --- | --- |
| 이미지 업로드·삭제·복사 | 관련 HTTP 요청 trace | 기본 HTTP 오류·지연 지표와 오류 로그 |
| 거래 수락·완료 | 기본 HTTP 요청 trace | 기본 HTTP 오류·지연 지표 |
| 알림·채팅 SSE | HTTP 요청 trace와 비동기 문맥 전파 | 실행기 기본 active·queued 지표, 비동기 실패·거절 로그 |

- [x] 별도 JDBC·AWS 자동 계측 라이브러리를 추가하지 않고 Boot 기본 관측으로 런타임 호환 범위를 제한
- [x] 이미지 전용 observation을 제거하고 관련 HTTP 지표·trace·오류 로그로 확인; 세부 계측은 필요해질 때 검토
- [x] 거래 완료 건수는 DB에서 필요할 때 집계하고 별도 애플리케이션 카운터는 두지 않음
- [x] `@Async` 실행기에 문맥 전파 데코레이터를 적용해 AFTER_COMMIT·비동기 경계에서도 추적 문맥을 전달
- [x] SSE 전용 디스패처와 작업별 지표를 제거하고 실행기 기본 지표·거절 로그로 단순화
- [x] SSE 전달 실패는 연결을 정리하고, 저장된 알림·채팅은 재조회로 복구

DB 호출 시간만으로 잠금 대기를 단정하지 않는다. PostgreSQL 잠금·교착·연결 지표를 함께 확인한다. 모든 메서드 추적, 서비스 맵 자동 생성, outbox·재전송 도입은 초기 범위에서 제외한다.

### 완료 기준

- [x] SSE 실행기의 active·queued 지표와 작업 실패·거절 로그를 확인할 수 있다.

## 7. 4단계: 대시보드·알림

### 대시보드

1. 서비스 상태: 요청 수, 5xx, route별 p95·p99, 외부 접속 상태, 배포 버전·시점
2. 서버·DB: CPU, 메모리, 디스크, 컨테이너 재시작·OOM, GC, Hikari 연결 대기, PostgreSQL 잠금·교착
3. 핵심 기능: SSE 공용 실행기 상태
4. 수집 상태: scrape 실패, 로그·trace 수집 오류·드롭, 저장소 용량

### 초기 알림 규칙

다음 수치는 초기 제안이며 실제 운영 기준선으로 조정한다. 기존 로컬 k6 결과를 운영 SLO로 그대로 사용하지 않는다.

| 알림 | 초기 조건 | 확인 대상 |
| --- | --- | --- |
| 외부 접속 불가 | 1분 간격 검사 3회 연속 실패 | HTTPS·Nginx·앱 상태 |
| 서버 오류 증가 | 최근 5분 요청 100건 이상에서 5xx 5% 초과, 또는 5xx 10건 이상 | 배포 시점·오류 로그·trace |
| 조회 API 지연 | 최근 5분 해당 route 요청 100건 이상, p95 1초 초과가 5분 지속 | DB·S3·GC·trace |
| 디스크 부족 | 80% 경고, 90% 긴급 | 관측 저장소·Docker·Nginx 로그 |
| DB 연결 부족 | 연결 대기 5분 지속 또는 연결 시간 초과 발생 | 풀 포화·느린 쿼리·잠금 |
| 수집 장애 | scrape 또는 수집 오류 3분 지속, 데이터 없음 별도 처리 | 수집기·네트워크·저장소 |

- [ ] 팀 알림 채널과 담당자를 연결하고 실제 테스트 알림 수신 확인
- [x] 환경·서비스·알림명별 그룹화와 심각도별 재전송 간격 설정
- [x] Discord receiver의 복구 알림 전송 설정 (`send_resolved: true`)
- [ ] 운영에서 복구 알림 실제 수신 확인 및 점검 시간 mute 설정
- [x] 알림에 심각도와 대응 문서 링크 포함
- [ ] 실제 Grafana 주소가 확정되면 알림에 대시보드 링크 추가
- [x] 별도 telemetry-missing 규칙으로 데이터 없음이 정상으로 처리되지 않도록 설정
- [x] 장애 종류별 확인 순서·완화 조치·복구 확인 절차 작성

## 8. 수정·추가 대상

다음은 구현 시의 예정 경로이다. 신규 설정·클래스의 구체 이름은 책임에 맞춰 확정한다.

| 경로 | 변경 내용 |
| --- | --- |
| `build.gradle` | 지표·추적 의존성, 기존 perf 설정과 중복 확인 |
| `src/main/resources/application.yaml` | 관측 환경 변수·JSON 로그·관리 포트·샘플링 |
| `src/main/java/com/anabada/fleaflea/global/config/` | 관측 설정·`SecurityConfig`의 `/readyz` 허용·관리 포트 보안 체인·비동기 문맥 전파 |
| `src/main/java/com/anabada/fleaflea/global/exception/GlobalExceptionHandler.java` | 오류 분류·중복 로그·필드 정리 |
| `src/main/java/com/anabada/fleaflea/domain/notification/`, `src/main/java/com/anabada/fleaflea/domain/chat/event/` | `@Async`와 공용 SSE 실행기 |
| `.env.example` | 비밀 값 없는 관측 설정 예시 |
| `deploy/ec2/compose.yml` | Alloy·exporter, 제한된 8081 관리 포트, `/readyz` 검사, 로그 회전·자원 제한 |
| `deploy/ec2/deploy.sh`, `.github/workflows/cd.yml` | 수집 설정 배포·버전 전달·새 이미지의 `/readyz` 검사와 이전 이미지 경로로 롤백 검사 |
| `deploy/monitoring/` (신규) | 모니터링 서버 Compose·Prometheus·Alloy·Loki·Tempo·Grafana 설정 |
| `deploy/ec2/README.md`, `deploy/monitoring/README.md` (신규) | 설치·접근·운영·롤백·장애 대응 |
| `src/test/` | 보안·문맥 전파·계측 정확성·회귀 테스트 |

현재 CD는 앱 Compose와 deploy.sh만 전달하므로, 신규 Alloy·exporter 설정도 명시적으로 배포해야 한다. 모니터링 서버의 Compose는 별도 배포 절차로 관리해 앱 배포 시 저장소를 재생성하지 않는다. deploy.sh는 deploy.env를 APP_IMAGE 하나로 다시 쓰므로, 관측 설정을 이 파일에 추가했다가 잃지 않도록 별도 설정 파일을 사용한다.

## 9. 검증 계획

테스트 작성은 프로젝트 컨벤션과 `docs/TESTING.md`를 따른다. DB 트랜잭션·동시성은 실제 PostgreSQL Testcontainers로 확인한다.

- [x] 표적 테스트와 런타임 검증: 추적 문맥 전파·정리, 관리 포트 접근 제한, `/readyz` 상태·DB 장애
- [x] 알림·채팅 비동기 trace 연결 구성
- [ ] 운영과 같은 10% 샘플링에서 트레이스 없는 오류도 로그·지표로 조사되고, 100% 샘플링 검증 환경에서는 로그↔trace 연결이 되는지 확인
- [x] 전체 회귀: `./gradlew test` 실행, Docker·외부 환경 제약과 미실행 항목 기록
- [x] 설정 검증: Compose 렌더링, Alloy·Prometheus·Alertmanager·Loki·Tempo 설정과 Grafana provisioning 확인
- [x] 로컬 구성에서 로그·지표·trace를 실제 생성해 Prometheus·Loki·Tempo 저장과 Grafana provisioning 확인
- [x] 컨테이너 재시작 후 데이터 보존과 수집 재개 확인
- [ ] `/readyz`가 실패하는 새 이미지 배포를 재현하고, 이전 이미지가 `/actuator/health`만 제공하는 경우에도 롤백 검사가 성공하는지 확인
- [ ] 테스트 환경에서 앱 포트 중단과 S3 실패를 재현. DB 연결 실패·복구는 검증 완료
- [x] Alloy 중단 시 업무 요청이 계속 처리되는지 확인. 중앙 저장소별 중단·복구 검증은 운영 전 staging에서 수행
- [ ] 외부 접속 실패부터 알림 수신·로그·trace 분석·복구 알림까지 종단 간 검증
- [ ] 기존 k6 시나리오를 같은 데이터·VU·시간·대기 조건으로 전후 비교
- [ ] p95·실패율·CPU·메모리·일일 저장량 기록, 유의미한 악화가 있으면 bucket·계측 범위·샘플링 조정

참고 파일: `performance/k6/README.md`, `ITEM-PERFORMANCE.md`, `ITEM-RESULTS.md`, `RESULTS.md`. 쓰기 부하·장애 주입은 테스트 환경에서만 수행한다. 컴파일·테스트 성공만으로 운영 수집과 실제 알림 수신이 검증됐다고 판단하지 않는다.

## 10. 배포·롤백과 작업 단위

| 작업 단위 | 산출물 | 선행 조건 |
| --- | --- | --- |
| 1. 앱 관측 기반 | JSON 로그·HTTP trace·Prometheus 지표·접근 제어·표적 테스트 | 현재 버전 호환성 확인 |
| 2. 수집 환경 | Compose·수집 설정·저장 정책·기본 Grafana·로컬 연결 검증 | 1단계 |
| 3. 기능 계측 | SSE 기본 실행기 지표·로그 | 1·2단계 |
| 4. 운영 완성 | 알림·대응 문서·장애 재현 결과·운영 배포 절차 | 운영 값 확정, 1~3단계 |

운영 적용 순서는 모니터링 저장소·Grafana → Alloy·exporter → Nginx의 `/readyz` 라우팅 준비 → 앱 관측 설정과 `/readyz`를 사용하는 Compose·배포 검사 전환 → 외부 검사 전환 → 알림 활성화로 한다. 전환 중에는 기존 8080 `/actuator/health`를 사용하는 외부 검사가 있는지 확인해 같은 배포에 검사 경로를 맞춘다. 앱 계측과 수집 설정을 별도로 되돌릴 수 있게 하고, 기존 앱 이미지·설정 버전과 이전 이미지의 health 경로를 기록한다. 롤백 시 배포 스크립트가 이전 이미지의 경로로 검사하며 업무 DB 볼륨과 관측 저장소를 삭제하지 않는다. 외부 연결·과금 자원·운영 배포는 실제 구현 단계의 요청 범위에 따라 진행한다.

최종 완료는 실제 테스트 장애를 알림으로 감지하고, 로그와 trace로 원인을 설명하며, 복구 후 정상 상태를 확인한 시점으로 정의한다.

## 11. 공식 참고 문서

- [Spring Boot Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Spring Boot Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Spring Boot 관리 포트·health group](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Grafana Alloy Docker 수집](https://grafana.com/docs/alloy/latest/monitor/monitor-docker-containers/)
- [Grafana Tempo 계측·전송](https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/)
- [Grafana Tempo 아키텍처](https://grafana.com/docs/tempo/latest/introduction/architecture/)

최신 문서의 설정을 그대로 복사하지 않고 실제 선택한 버전의 지원 여부를 구현 시 확인한다.
