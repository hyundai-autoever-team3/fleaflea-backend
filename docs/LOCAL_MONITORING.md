# Fleaflea 로컬 모니터링 사용 가이드

이 문서는 Fleaflea 백엔드를 로컬에서 실행하면서 로그, 지표, 트레이스를
Grafana에서 확인하는 방법을 설명합니다. 모든 명령은 저장소 루트에서
실행합니다.
운영 EC2 연결 절차는 [EC2 모니터링 연결 가이드](EC2_MONITORING_CONNECTION.md)를
참고합니다.

화면에서 DB·JVM·SSE 큐·Blackbox·Loki·Tempo의 수치를 읽는 방법은
[모니터링 화면 읽기 가이드](MONITORING_VIEWING_GUIDE.md)에 정리했습니다.

## 1. 로컬 구성

```text
Spring Boot
  ├─ 8080: API, /readyz
  ├─ 8081: /actuator/health, /actuator/prometheus
  ├─ build/logs/fleaflea.json ─→ Alloy ─→ Loki
  └─ OTLP/HTTP 4318 ──────────→ Alloy ─→ Tempo

PostgreSQL 5432 ─→ PostgreSQL Exporter ─→ Alloy ─→ Prometheus

Grafana
  ├─ Prometheus: 지표
  ├─ Loki: 로그
  ├─ Tempo: 트레이스
  └─ Alertmanager: 현재 알림 상태
```

로컬 앱은 Gradle로 호스트에서 실행합니다. 모니터링 구성은 Docker Compose로
실행합니다. Alloy는 `host.docker.internal`을 통해 앱의 관리 포트에 접근하고,
PostgreSQL Exporter는 같은 주소의 `5432` 포트에서 DB 지표를 읽습니다.
Alloy는 Compose 네트워크에서 Exporter의 지표를 수집합니다.

## 2. 준비 사항과 포트

필요한 프로그램은 다음과 같습니다.

- Docker Desktop 또는 Docker Engine과 Docker Compose
- JDK 25. Gradle은 저장소의 wrapper를 사용합니다.

사용하는 로컬 포트는 다음과 같습니다.

| 포트 | 용도 |
| --- | --- |
| 8080 | Fleaflea API와 `/readyz` |
| 8081 | Actuator health와 Prometheus 지표 |
| 3000 | Grafana |
| 9090 | Prometheus |
| 9093 | Alertmanager |
| 3100 | Loki |
| 3200 | Tempo 조회 API |
| 4318 | Alloy OTLP/HTTP 수신 |
| 12346 | 로컬 Alloy UI와 자체 지표 |
| 5432 | 로컬 PostgreSQL |

다른 프로그램이 이 포트를 사용 중이면 먼저 종료하거나 해당 프로그램의
포트를 변경해야 합니다.

## 3. 최초 한 번 설정

### 3.1 애플리케이션 환경 파일

루트에 `.env`가 없다면 예제를 복사합니다. 이미 `.env`가 있으면 덮어쓰지
말고 아래 관측 설정만 추가합니다.

```bash
cp .env.example .env
```

`.env`에서 DB 설정을 확인합니다.

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/fleaflea_db
DB_USERNAME=postgres
DB_PASSWORD=password
DB_CONNECTION_TIMEOUT_MS=3000
```

다음 관측 설정을 추가하거나 주석 해제합니다.

```dotenv
APP_ENV=local
APP_VERSION=local
MANAGEMENT_PORT=8081

OTEL_SERVICE_NAME=fleaflea
OTEL_RESOURCE_ATTRIBUTES=service.version=local,deployment.environment.name=local
OTEL_TRACES_SAMPLER=always_on
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:4318/v1/traces
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_METRICS_EXPORTER=none
OTEL_LOGS_EXPORTER=none
LOG_CORRELATION_PATTERN='[%X{trace_id:-}-%X{span_id:-}] '

LOGGING_FILE_NAME=build/logs/fleaflea.json
LOGGING_STRUCTURED_FORMAT_FILE=ecs
```

로컬에서는 `OTEL_TRACES_SAMPLER=always_on`으로 요청을 모두 수집합니다.
운영 기본 샘플링 비율은 10%입니다. `LOG_CORRELATION_PATTERN`은 일반 콘솔
로그에 agent의 trace ID와 span ID를 표시합니다.

터미널 로그도 운영과 같은 JSON으로 보고 싶다면 다음 값을 추가합니다.

```dotenv
LOG_FORMAT_CONSOLE=ecs
```

이 값을 생략하면 터미널은 읽기 쉬운 일반 로그를 출력하고,
`build/logs/fleaflea.json` 파일만 ECS JSON 형식으로 기록합니다.

### 3.2 Grafana 환경 파일

로컬 모니터링 환경 파일을 만듭니다.

```bash
cp deploy/monitoring/.env.example deploy/monitoring/.env.local
```

`deploy/monitoring/.env.local`에서 다음 값을 변경합니다.

```dotenv
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=local-grafana-password
GRAFANA_ROOT_URL=http://localhost:3000

TELEMETRY_BIND_ADDRESS=127.0.0.1
GRAFANA_BIND_ADDRESS=127.0.0.1
```

`.env`와 `.env.local`은 Git에서 제외됩니다. 비밀번호를 저장소에 커밋하지
않습니다.

### 3.3 Discord 웹훅 파일

`deploy/monitoring/secrets/discord-webhook-url` 파일을 만들고 Discord 웹훅
URL을 한 줄로 저장합니다. 이 디렉터리는 Git에서 제외됩니다. 저장한 뒤
`chmod 600 deploy/monitoring/secrets/discord-webhook-url`로 파일 권한을
설정합니다. 이 파일이 없으면 Alertmanager가 시작되지 않습니다.

## 4. 실행 방법

### 4.1 PostgreSQL 확인

호스트의 PostgreSQL이 `5432` 포트에서 실행 중이고 `fleaflea_db`가 있는지
확인합니다.

```bash
psql -h 127.0.0.1 -p 5432 -U postgres -d fleaflea_db -c 'SELECT 1'
```

앱과 Exporter가 같은 DB를 읽도록 `deploy/monitoring/.env.local`의
`LOCAL_DB_USERNAME`, `LOCAL_DB_PASSWORD`를 `.env`의 DB 계정에 맞춥니다.
Exporter 컨테이너는 `host.docker.internal:5432`로 연결하므로 PostgreSQL이
Docker에서 들어오는 연결을 허용해야 합니다. 연결되지 않으면 PostgreSQL의
`listen_addresses`와 `pg_hba.conf`를 확인하고, DB 포트는 외부 인터넷에
공개하지 않습니다.

`performance/compose.yml`의 PostgreSQL은 성능 측정용 별도 DB로, 호스트
포트 `5435`를 계속 사용합니다. 이 로컬 모니터링 구성에서는 실행할 필요가
없습니다.

이미지 업로드 기능도 로컬에서 사용하려면 MinIO를 함께 실행합니다.

```bash
docker compose -f performance/compose.yml up -d minio minio-init
```

### 4.2 모니터링 스택 실행

Alloy가 읽을 로그 디렉터리를 먼저 만듭니다.

```bash
mkdir -p build/logs
```

중앙 모니터링 구성과 로컬 override를 함께 실행합니다.

```bash
docker compose \
  --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/compose.local.yml \
  up -d --wait
```

컨테이너 상태를 확인합니다.

```bash
docker compose \
  --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/compose.local.yml \
  ps
```

### 4.3 애플리케이션 실행

별도 터미널에서 실행합니다.

```bash
./gradlew bootRun -PotelAgent
```

IntelliJ의 Application 실행을 사용할 때는 먼저 `./gradlew prepareOtelAgent`로
agent를 받고, Run Configuration의 VM options에
`-javaagent:<저장소_절대경로>/build/otel/opentelemetry-javaagent.jar`를 넣습니다.
위 `OTEL_*` 값과 `LOG_CORRELATION_PATTERN`도 Run Configuration의 환경
변수에 설정해야 합니다. Gradle `bootRun`은 루트 `.env`를 읽지만 IntelliJ의
일반 Application 실행은 `.env`를 자동으로 읽지 않습니다.

애플리케이션 시작 후 다음 세 경로를 확인합니다.

```bash
curl -fsS http://localhost:8080/readyz
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8081/actuator/prometheus | head
```

정상일 때 `/readyz`와 Actuator health는 `UP`을 반환합니다. `/readyz`는 DB
연결도 검사하므로 PostgreSQL이 중단되면 503을 반환합니다.

### 4.4 확인용 요청 발생

지표, 로그, 트레이스가 만들어지도록 몇 가지 요청을 보냅니다.

```bash
curl -i http://localhost:8080/readyz
curl -i http://localhost:8080/api/v1/items/1
curl -i http://localhost:8080/api/v1/auth/login
```

인증 없는 `/api/v1/items/1`은 401, GET 방식의 로그인 요청은 405가 정상입니다.
`/readyz`는 요청 완료 로그 수집 대상에서 제외됩니다.
Alloy의 앱 지표 수집 주기는 5초, PostgreSQL Exporter 지표 수집 주기는
10초입니다. 요청 후 잠시 기다렸다가 Grafana를 확인합니다.

## 5. Grafana에서 확인하기

브라우저에서 `http://localhost:3000`을 열고 `.env.local`에 설정한 계정으로
로그인합니다.

기존 Grafana 볼륨을 재사용한다면 관리자 비밀번호가 `.env.local`의 값과
다를 수 있습니다. 이때는 기존에 설정한 비밀번호로 로그인합니다.

### 5.1 기본 대시보드

왼쪽 메뉴에서 **Dashboards → Fleaflea → Fleaflea Overview**를 선택합니다.
상단의 **Environment**에서 로컬·운영을 고를 수 있습니다.
대시보드 제목·패널명·범례는 영어로, Fleaflea에서 작성한 사용 설명은
한국어로 표기합니다. PromQL의 지표명과 라벨명은 실제 수집 이름을 그대로
사용합니다.
세 화면은 `deploy/monitoring/grafana/dashboards/`의 JSON 파일로 관리합니다.
패널을 바꾸려면 해당 파일을 수정하세요. Grafana는 약 30초마다 파일을
다시 읽으며, 화면에서 직접 수정한 내용은 저장하지 않도록 설정되어 있습니다.

다음 항목을 한 화면에서 확인할 수 있습니다.

- 외부 서비스 상태와 현재 발생 중인 알림
- `/readyz`를 제외한 API 요청량·5xx 비율
- 실제 API 경로별 p95와 p99
- JVM 힙 사용률과 HikariCP 연결 상태
- SSE 실행기 대기·활성 작업
- 배포 버전
- 컨테이너 CPU와 메모리
- 애플리케이션 로그

로컬 override는 cAdvisor를 실행하지 않으므로 **Container CPU (Production)**과
**Container Memory (Production)** 패널은
비어 있습니다. 해당 패널은 운영 EC2의 monitoring profile에서 채워집니다.
앱·DB·JVM 지표 확인에는 영향이 없습니다.

서버 CPU·메모리·디스크의 상세 지표는 **Fleaflea Host Details**에서 봅니다.
로컬에서는 `job=monitoring-node`를 선택하며, 표시되는 호스트는 macOS가
아닌 Docker Desktop Linux VM입니다. 운영 EC2는 `job=fleaflea-node`를
선택합니다. 이 화면은 Grafana 커뮤니티의 Node Exporter Full(1860)을
Fleaflea 데이터 소스에 맞춰 가져온 것입니다.

DB 연결·트랜잭션·캐시·잠금·테이블 상태는 **Fleaflea PostgreSQL Details**에서
봅니다. 로컬 DB는 `5432` 포트의 PostgreSQL이며, 로컬 exporter → Alloy →
Prometheus 경로로 지표가 들어옵니다. 운영에서는 DB가 있는 EC2의 exporter와
Alloy가 같은 역할을 합니다. 화면의 `Database`는 `fleaflea_db`를 선택합니다.

### 5.2 Loki에서 로그 보기

왼쪽 메뉴에서 **Explore**를 열고 데이터 소스로 **Loki**를 선택합니다.

전체 애플리케이션 로그:

```logql
{service="fleaflea"} | json
```

ERROR 로그:

```logql
{service="fleaflea"} | json | log_level="ERROR"
```

HTTP 5xx 요청 완료 로그:

```logql
{service="fleaflea"} | json | status >= 500
```

특정 오류 코드:

```logql
{service="fleaflea"} | json | errorCode="INTERNAL_SERVER_ERROR"
```

특정 trace ID:

```logql
{service="fleaflea"} | json | trace_id="TRACE_ID"
```

로그 상세 화면의 `TraceID` 링크를 누르면 같은 요청의 Tempo trace로 이동할
수 있습니다. trace 샘플링이 100%가 아니면 로그에 trace ID가 있어도 Tempo에
trace가 없을 수 있습니다.

로컬 로그 파일을 터미널에서 직접 볼 수도 있습니다.

```bash
tail -f build/logs/fleaflea.json
```

### 5.3 Prometheus 지표 보기

Explore에서 데이터 소스로 **Prometheus**를 선택합니다.

앱 수집 상태:

```promql
up{job="fleaflea-app"}
```

실행 중인 배포 버전:

```promql
fleaflea_build_info{service="fleaflea"}
```

초당 요청 수:

```promql
sum(rate(http_server_requests_seconds_count{service="fleaflea"}[5m]))
```

route별 p95:

```promql
histogram_quantile(
  0.95,
  sum by (le, uri) (
    rate(http_server_requests_seconds_bucket{service="fleaflea"}[5m])
  )
)
```

DB 연결 대기 수:

```promql
hikaricp_connections_pending{service="fleaflea"}
```

SSE 실행기의 대기 작업 수:

```promql
executor_queued_tasks{service="fleaflea",name="notificationSseExecutor"}
```

Prometheus 자체 화면 `http://localhost:9090`에서도 같은 PromQL을 실행할 수
있습니다.

### 5.4 Tempo에서 트레이스 보기

Explore에서 데이터 소스로 **Tempo**를 선택합니다.

1. **Search**를 선택합니다.
2. Service Name으로 `fleaflea`를 선택합니다.
3. 검색 결과에서 `http get`, `http post` 등의 요청을 엽니다.
4. span의 소요 시간과 오류 상태를 확인합니다.
5. **Logs for this span**을 사용하면 같은 trace ID의 Loki 로그를 조회할 수
   있습니다.

span 상세의 **Metrics for this span**에서는 같은 서비스의 span 발생률과
p95 지연을 Prometheus에서 조회할 수 있습니다. Tempo의 metrics-generator가
trace에서 지표를 만들어 Prometheus로 전송하므로, 설정을 적용한 뒤 들어온
trace부터 지표가 생깁니다. Prometheus 지표는 개별 trace ID별로 저장되지
않습니다. 지표의 exemplar가 생성된 경우 그래프에서 해당 Tempo trace로
이동할 수 있습니다.

이미지 작업은 관련 HTTP 요청의 trace와 오류 로그로 확인합니다. Java agent는
JDBC와 지원되는 외부 클라이언트 호출을 별도 span으로 기록할 수 있습니다.
이미지 검증 같은 내부 로직은 별도 계측이 없으면 span으로 나뉘지 않습니다.
SSE도 기본 HTTP 요청 trace와 로그로 확인합니다.
Alloy는 `/readyz`, `/actuator/health`, `/actuator/prometheus`, 주기적인
`NotificationSseHeartbeat`, Hikari 연결 풀 유지 작업의 단독 JDBC trace를
통째로 제외합니다. 일반 API 요청의 JDBC span과 다른 스케줄러 trace는
계속 전송되며, 약 10초의 수집 대기 후 나타납니다.

### 5.5 알림 상태 보기

로컬에서도 Prometheus 알림 규칙은 평가됩니다. Grafana의 Alerting 화면이나
`http://localhost:9090/alerts`에서 상태를 확인할 수 있습니다.

Alertmanager의 `discord` receiver는 Discord 웹훅으로 알림 발생과 해제를
전송합니다. 실행 전에 `deploy/monitoring/secrets/discord-webhook-url` 파일에
Discord 웹훅 URL을 한 줄로 저장하고 파일 권한을 `600`으로 설정하세요. 이
디렉터리는 Git에서 제외됩니다. 웹훅 파일이 없으면 Alertmanager가 시작되지
않습니다. 운영 알림 확인 절차는 `deploy/monitoring/README.md`와
`deploy/monitoring/RUNBOOK.md`를 따릅니다.

## 6. 빠른 상태 점검

각 구성 요소의 readiness를 확인합니다.

```bash
curl -fsS http://localhost:9090/-/ready
curl -fsS http://localhost:3100/ready
curl -fsS http://localhost:3200/ready
curl -fsS http://localhost:3000/api/health
curl -fsS http://localhost:8080/readyz
```

Alloy가 앱과 DB를 수집하는지는 다음 지표로 확인합니다.

```promql
up{job=~"fleaflea-app|fleaflea-postgres"}
```

두 결과가 모두 `1`이면 정상입니다.

## 7. 자주 발생하는 문제

### Grafana가 다른 주소로 이동한다

`deploy/monitoring/.env.local`을 확인합니다.

```dotenv
GRAFANA_ROOT_URL=http://localhost:3000
```

수정 후 Grafana를 다시 만듭니다.

```bash
docker compose \
  --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/compose.local.yml \
  up -d --force-recreate grafana
```

### 앱 지표가 보이지 않는다

먼저 관리 포트를 직접 확인합니다.

```bash
curl -fsS http://localhost:8081/actuator/prometheus | head
```

그다음 Alloy 로그를 확인합니다.

```bash
docker logs --tail 100 fleaflea-local-alloy
```

`MANAGEMENT_PORT=8081`인지, 앱이 실행 중인지, PromQL의
`up{job="fleaflea-app"}` 값이 1인지 확인합니다.

### 로그가 보이지 않는다

로그 파일 존재 여부를 확인합니다.

```bash
ls -l build/logs/fleaflea.json
tail -n 5 build/logs/fleaflea.json
```

`.env`에 다음 두 설정이 있어야 합니다.

```dotenv
LOGGING_FILE_NAME=build/logs/fleaflea.json
LOGGING_STRUCTURED_FORMAT_FILE=ecs
```

Alloy를 앱보다 먼저 실행하지 않았더라도 파일의 새 로그부터 수집됩니다.

### 트레이스가 보이지 않는다

`.env` 설정을 확인합니다.

```dotenv
OTEL_SERVICE_NAME=fleaflea
OTEL_TRACES_SAMPLER=always_on
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:4318/v1/traces
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
```

Alloy가 4318 포트를 열고 있는지 확인합니다.

```bash
docker ps --filter name=fleaflea-local-alloy
docker logs --tail 100 fleaflea-local-alloy
```

환경 변수를 바꿨다면 앱을 다시 시작해야 합니다.
`./gradlew bootRun -PotelAgent`로 실행했는지, IntelliJ에서는 agent VM 옵션을
설정했는지도 확인합니다. Alloy와 Tempo를 다시 시작할 필요는 없습니다.

### PostgreSQL 지표가 보이지 않는다

DB와 exporter 상태를 확인합니다.

```bash
psql -h 127.0.0.1 -p 5432 -U postgres -d fleaflea_db -c 'SELECT 1'
docker logs --tail 100 fleaflea-local-postgres-exporter
```

기본 계정이 아닌 경우 `deploy/monitoring/.env.local`에 다음 값을 추가합니다.

```dotenv
LOCAL_DB_USERNAME=사용자명
LOCAL_DB_PASSWORD=비밀번호
```

호스트에서는 DB에 연결되지만 Exporter에서 연결할 수 없다면
`host.docker.internal:5432`로 접속할 수 있도록 PostgreSQL의
`listen_addresses`와 `pg_hba.conf`를 확인합니다.

### 특정 포트를 이미 사용 중이다

macOS와 Linux에서는 다음과 같이 확인할 수 있습니다.

```bash
lsof -nP -iTCP:3000 -sTCP:LISTEN
lsof -nP -iTCP:4318 -sTCP:LISTEN
lsof -nP -iTCP:8081 -sTCP:LISTEN
```

기존 프로세스를 종료한 뒤 모니터링 스택을 다시 시작합니다.

## 8. 재시작과 종료

모니터링 컨테이너만 재시작합니다.

```bash
docker compose \
  --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/compose.local.yml \
  restart
```

컨테이너를 종료하되 지표·로그·트레이스 볼륨은 보존합니다.

```bash
docker compose \
  --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/compose.local.yml \
  down
```

다시 시작하면 기존 named volume을 재사용합니다.

`docker compose down -v`는 Prometheus, Loki, Tempo, Grafana와 Alloy의 로컬
데이터를 삭제합니다. 관측 데이터를 완전히 초기화하려는 경우에만 사용합니다.
호스트 PostgreSQL은 이 명령의 영향을 받지 않습니다. 성능 측정용 PostgreSQL
데이터 볼륨은 별도의 performance Compose에서 관리됩니다.

## 9. 로컬 시작 체크리스트

- [ ] Docker가 실행 중이다.
- [ ] 호스트 PostgreSQL이 `5432` 포트에서 연결을 받는다.
- [ ] `deploy/monitoring/.env.local`의 Grafana URL이 localhost다.
- [ ] `build/logs` 디렉터리를 만들었다.
- [ ] 모니터링 Compose가 정상 실행 중이다.
- [ ] 앱의 `/readyz`가 UP이다.
- [ ] Prometheus의 `up{job="fleaflea-app"}` 값이 1이다.
- [ ] Loki에서 `{service="fleaflea"} | json` 로그가 보인다.
- [ ] Tempo에서 `service.name=fleaflea` trace가 보인다.
