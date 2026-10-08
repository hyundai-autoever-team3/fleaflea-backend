# Fleaflea 모니터링 화면 읽기 가이드

로컬 모니터링을 실행한 뒤 Grafana에서 요청, DB, JVM, SSE 실행기,
외부 상태 검사, 로그와 트레이스를 확인하는 방법입니다.
실행·로그인·종료 절차는
[로컬 모니터링 사용 가이드](LOCAL_MONITORING.md)를 참고합니다.

## 먼저 화면 열기

1. [Grafana](http://localhost:3000)에 로그인합니다.
2. **Dashboards → Fleaflea → Fleaflea Overview**를 엽니다.
   상단 **Environment**에서 `local` 또는 `production`을 선택합니다.
   서버 자원은 **Fleaflea Host Details**, PostgreSQL 서버 상태는
   **Fleaflea PostgreSQL Details**에서 봅니다. 서버 상세 화면의 **Job**은
   로컬에서 `monitoring-node`, 운영에서 `fleaflea-node`를 선택합니다.
   로컬 서버 지표는 macOS 본체가 아닌 Docker Desktop의 Linux VM입니다.
3. 오른쪽 위 시간 범위를 **Last 15 minutes**로 바꿉니다. 과거 데이터가
   필요하면 범위를 넓힙니다.
4. 상세 지표나 로그는 왼쪽 메뉴 **Explore**에서 데이터 소스를
   **Prometheus**, **Loki**, **Tempo** 중 하나로 선택해 조회합니다.
   Prometheus 쿼리는 Code 모드에 붙여 넣고 **Run query**를 누릅니다.
   코드 블록에 쿼리가 여러 줄 있으면 한 줄씩 별도로 실행합니다.

숫자 `0`은 조회한 지표 값이 0이라는 뜻입니다. **No data**는
조회 기간에 시계열이나 로그가 없다는 뜻이므로 정상이라는 증거가 아닙니다.
막 시작한 앱, 발생한 적 없는 오류, 요청량이 적은 구간에서도 No data가
나올 수 있습니다. 먼저 시간 범위와 아래 `up` 지표를 확인합니다.

쿼리의 `service="fleaflea"`는 애플리케이션을 고릅니다. 로컬과 운영 데이터가
같은 Prometheus에 들어온다면 `environment="local"` 또는
`environment="production"` 조건을 추가하고, 인스턴스별 차이를 볼 때는
`instance` 라벨로 좁힙니다.

화면의 제목·패널명·범례는 영어이고, Fleaflea에서 작성한 패널 설명과 이
가이드는 한국어입니다. 커뮤니티 템플릿 Node Exporter Full(1860)은 원본의
영어 설명을 유지합니다. Spring Boot Statistics(19004)는 `application`,
`namespace` 라벨을 전제로 하지만 현재 앱 지표에는 `service`,
`environment`, `instance`가 붙으므로 그대로 Import하지 않았습니다.
앱 지표는 **Fleaflea Overview**에서 확인합니다.

## 1. 수집 상태와 외부 상태 검사

상단 **External Health**와 **Firing Alerts (All)** 패널을 본 뒤
**Endpoint and Scrape Status** 패널을 확인합니다. 알림 수는 선택한 환경과
관계없이 전체 환경의 firing 알림 수입니다.
Prometheus Explore에서 다음 쿼리를 각각 실행할 수도 있습니다.

```promql
up{job="fleaflea-app"}
up{job="fleaflea-postgres"}
pg_up{job="fleaflea-postgres"}
probe_success{job="blackbox-http",service="fleaflea"}
```

| 값 | 의미 |
| --- | --- |
| 앱 `up=1` | Alloy가 `/actuator/prometheus`를 읽어 지표를 전송 중 |
| DB Exporter `up=1` | PostgreSQL Exporter의 HTTP 지표를 수집 중 |
| `pg_up=1` | Exporter의 마지막 PostgreSQL 연결 확인이 성공함 |
| `probe_success=1` | Blackbox가 외부 `/readyz`에서 성공 응답을 받음 |

`up=0`은 지표 수집 실패, `pg_up=0`은 Exporter의 DB 연결 확인 실패,
`probe_success=0`은 대상 검사 실패입니다. 서로 다른 신호입니다.
`pg_up=1`만으로 모든 DB 쿼리의 성공을 보장하지 않으므로 `/readyz`와
애플리케이션 오류도 함께 봅니다.
Blackbox의 HTTP 상태 코드와 검사 시간은 다음처럼 봅니다.

```promql
probe_http_status_code{job="blackbox-http",service="fleaflea"}
probe_duration_seconds{job="blackbox-http",service="fleaflea"}
```

로컬 Blackbox 대상은 `http://host.docker.internal:8080/readyz`, 운영 대상은
`https://api.fleaflea.app/readyz`입니다. `probe_success=0`이면 상태 코드와
앱·DB `up`을 비교합니다. `probe_success=0`인데 앱 `up=1`이면 외부 경로,
앱 준비 상태, DB의 `pg_up`을 차례로 확인합니다. 앱 `up` 자체가 없다면 Alloy와
앱의 관리 포트부터 확인합니다.

## 2. 요청량·5xx·지연 확인하기

대시보드의 **API Request Rate**, **HTTP 5xx Rate**, **API Latency by Route** 패널을 함께
봅니다. 요청률은 초당 요청 수, 5xx 비율은 `/readyz`를 제외한 요청 중
서버 오류의 비율입니다. 경로별 응답 시간은 p95·p99를 표시합니다.

```promql
sum(rate(http_server_requests_seconds_count{service="fleaflea",uri!="/readyz"}[5m]))
(sum(rate(http_server_requests_seconds_count{service="fleaflea",status=~"5..",uri!="/readyz"}[5m])) or vector(0)) / clamp_min(sum(rate(http_server_requests_seconds_count{service="fleaflea",uri!="/readyz"}[5m])), 0.001)
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{service="fleaflea",uri!~"/readyz|/actuator/.*|UNKNOWN|/api/v1/notifications/subscribe"}[5m])))
```

마지막 쿼리는 경로별 p95입니다. 대시보드는 p99도 함께 표시합니다.
SSE 구독 경로 `/api/v1/notifications/subscribe`는 연결 시간이 길어 일반
HTTP 응답 시간 지표에서 제외했습니다. `/readyz`와 `UNKNOWN`도 경로별
응답 시간에서 제외합니다. 5xx가 없으면 비율은 **0%**로 표시하지만,
실제 API 요청이 없으면 p95·p99 그래프는 비어 있습니다. 이때 요청률과
HTTP 완료 로그를 함께 확인합니다.

## 3. DB 지표: 앱 연결 풀과 PostgreSQL 구분하기

대시보드의 **HikariCP Connection Pool** 패널은 앱이 사용하는 HikariCP 연결 풀입니다.
Prometheus Explore에서는 다음을 실행합니다.

```promql
hikaricp_connections_active{service="fleaflea"}
hikaricp_connections_idle{service="fleaflea"}
hikaricp_connections_pending{service="fleaflea"}
hikaricp_connections_max{service="fleaflea"}
```

`active`는 앱이 사용 중인 연결, `idle`은 풀에 대기 중인 연결,
`pending`은 연결을 얻으려고 기다리는 요청입니다. `active`가 `max`에
근접하고 `pending`이 지속되면 느린 쿼리·긴 트랜잭션·DB 부하를 확인합니다.
현재 알림 규칙은 `pending > 0`이 **5분 동안** 지속될 때 경고합니다.
`idle`이 여러 개 남는 것은 풀의 유지 연결일 수 있어 그 자체로 장애가 아닙니다.

PostgreSQL 서버 쪽 지표는 **Fleaflea PostgreSQL Details**에서 연결 수,
트랜잭션, 캐시 적중률, 잠금, 테이블 상태를 확인합니다. `Database`를
`fleaflea_db`로 선택하세요. 필요한 원본 지표는 **Prometheus Explore**에서도
조회할 수 있습니다. `datname`을 지정하지 않으면 템플릿 DB까지 함께 표시됩니다.

```promql
pg_stat_database_numbackends{datname="fleaflea_db"}
pg_stat_activity_count{datname="fleaflea_db",state="idle in transaction"}
increase(pg_stat_database_deadlocks{datname="fleaflea_db"}[1h])
```

순서대로 현재 DB 연결 수, 트랜잭션을 열어둔 채 유휴 상태인 연결 수,
최근 1시간의 교착 증가량입니다. PostgreSQL 연결 수에는 앱 외의 연결도
포함되므로 HikariCP `active`와 같을 필요는 없습니다. `deadlocks`는 누적
카운터이므로 현재 원시값보다 `increase`로 최근 증가 여부를 봅니다.

## 4. JVM 힙과 SSE 작업 큐

대시보드의 **JVM Heap Usage** 패널은 최대 힙 크기 대비 현재 사용량을 비율로
표시합니다. Explore에서 원시 사용량과 최대치를 볼 수 있습니다.

```promql
sum(jvm_memory_used_bytes{service="fleaflea",area="heap"})
sum(jvm_memory_max_bytes{service="fleaflea",area="heap"} >= 0)
increase(jvm_gc_pause_seconds_count{service="fleaflea"}[5m])
```

GC로 힙 사용량이 주기적으로 내려가는 것은 자연스럽습니다. GC 후에도
사용량 기준선이 계속 올라가거나, GC 횟수와 요청 지연이 함께 증가하면
확인할 대상입니다. JVM의 일부 메모리 풀은 `max=-1`로 최대치가 정의되지
않으므로 위 최대 힙 쿼리는 그런 풀을 제외합니다. 힙 수치 하나만으로
메모리 누수라고 판단하지 않습니다.

SSE 알림·채팅·하트비트는 같은 `notificationSseExecutor`를 사용합니다.
대시보드 **SSE Executor** 패널은 실행 중인 스레드와 대기 작업 수를 보여줍니다.

```promql
executor_queued_tasks{service="fleaflea",name="notificationSseExecutor"}
executor_active_threads{service="fleaflea",name="notificationSseExecutor"}
executor_pool_max_threads{service="fleaflea",name="notificationSseExecutor"}
```

`queued`가 쌓이고 `active`가 늘어나는 상태가 계속되면 처리 속도가
유입 속도를 못 따라가는지 확인합니다. 실행기가 작업을 거절하면
`notification_sse_task_rejected` 경고 로그를 남깁니다. 알림과 채팅
데이터는 DB에 남으며, 클라이언트는
재연결 시 목록과 누락 메시지를 다시 조회해야 복구할 수 있습니다.

## 5. 배포 버전

**Deployment Version** 패널은 현재 실행 중인 앱의 버전과 환경·인스턴스를
보여줍니다. 로컬에서는 기본적으로 `local`, 운영에서는 배포 이미지의
버전이 표시됩니다.

```promql
fleaflea_build_info{service="fleaflea"}
```

## 6. Loki에서 로그 읽기

**Explore → Loki**를 선택하고 시간 범위를 맞춘 뒤 아래 LogQL을 실행합니다.
대시보드의 **Application Logs** 패널에서도 최근 앱 로그를 볼 수 있습니다.

```logql
{service="fleaflea"} | json
```

필요한 로그만 좁힙니다.

```logql
{service="fleaflea"} | json | log_level="ERROR"
{service="fleaflea"} | json | status >= 500
{service="fleaflea"} | json | errorCode="INTERNAL_SERVER_ERROR"
{service="fleaflea"} | json | trace_id="여기에_trace_id"
{service="fleaflea"} | json | message="notification_sse_task_rejected"
```

로컬 앱은 `build/logs/fleaflea.json`의 ECS JSON 로그를 Alloy가 읽습니다.
`HTTP GET /api/v1/items/{itemId} -> 200 (42 ms) traceId=...`와 같이 요청 메서드,
경로 패턴, 상태 코드, 소요 시간이 메시지에 표시됩니다. Java agent가 trace를
만든 요청에는 `traceId`가 뒤에 붙습니다. 로그를 펼치면 `route`, `method`, `status`,
`durationMs`를 확인할 수 있습니다. 해당 요청에 값이 있을 때는
`errorCode`, `trace_id`, `span_id`도 표시됩니다. `/readyz`와 `/actuator/**`는
이 요청 완료 로그에서 제외됩니다. 인증 단계에서 종료된 `401`·`403`은
라우팅 전이므로 `route=UNMATCHED`로 기록될 수 있습니다. `401`·`405`처럼
의도적으로 보낸 테스트 요청도 로그에 남으므로 상태 코드와 경로 패턴을
함께 판단합니다. 로그가 안 보이면 **시간 범위 → 앱 JSON 파일 → Alloy →
Loki** 순서로 점검합니다.

요청 필터가 4xx 결과를 INFO로 기록합니다. 예상 가능한 검증·바인딩·JSON
파싱 오류는 예외 핸들러에서 별도 INFO 로그를 남기지 않습니다. DB 무결성
위반은 원인 유형을 WARN으로, 5xx 예외는 스택을 ERROR로 남깁니다. Spring
MVC의 처리 완료 WARN은 `ExceptionHandlerExceptionResolver`에 한해 숨깁니다.

## 7. Tempo에서 요청 흐름 보기

**Explore → Tempo → Search**에서 Service Name을 `fleaflea`로 고르고
시간 범위를 맞춰 검색합니다. 결과에서 요청을 열어 전체 시간과 각 span의
소요 시간을 비교합니다. Loki 로그에 `trace_id`가 있으면 Tempo의 Trace ID
조회에 붙여 넣거나 로그 상세의 **TraceID** 링크를 사용합니다. Tempo에서
**Logs for this span**을 누르면 같은 trace의 Loki 로그로 돌아갈 수 있습니다.

span 상세의 **Metrics for this span**은 Tempo가 생성해 Prometheus로 보낸
span 발생률과 p95 지연을 해당 서비스 기준으로 보여줍니다. 이는 같은
서비스의 집계 지표이며 개별 trace ID의 지표가 아닙니다. 지표에 exemplar가
있으면 Prometheus 그래프에서 연결된 Tempo trace로 이동할 수 있습니다.

Alloy는 `/readyz`, `/actuator/health`와 `NotificationSseHeartbeat`가 포함된
반복 trace를 통째로 제외합니다. 헬스 상태는 Blackbox·Prometheus 지표로
확인하고, Tempo에서는 실제 API 요청과 다른 스케줄러 작업을 조회합니다.
Alloy가 trace 단위로 판단하므로 일반 trace도 약 10초의 대기 후 나타납니다.

이미지 업로드·삭제·복사는 관련 HTTP 요청 trace에서 전체 시간을 확인합니다.
이미지 검증 같은 내부 로직은 별도 계측이 없으면 span으로 나뉘지 않습니다.
Java agent가 지원하는 JDBC와 외부 클라이언트 호출은 별도 span으로 표시됩니다.
로컬 가이드의 추적 샘플링은 100%지만,
운영 기본값은 10%입니다. 따라서 운영 로그에 `trace_id`가 있어도 Tempo에
trace가 없을 수 있습니다. Tempo 검색 결과가 없을 때는 먼저 시간 범위,
요청 발생 여부, 샘플링과 Alloy의 OTLP 수신 상태를 확인합니다.

## 증상에서 시작하는 조회 순서

| 증상 | 먼저 볼 것 | 다음 확인 |
| --- | --- | --- |
| 서비스 접속 불가 | `probe_success`, 앱 `up`, `/readyz` | `probe_http_status_code`, `pg_up`, 최근 로그 |
| DB 요청 지연 | Hikari `pending`·`active` | PostgreSQL 연결·교착, 느린 요청의 trace |
| 요청이 느림 | API 경로별 응답 시간 p95와 요청 수 | JVM 힙·GC, Hikari `pending`, Tempo span |
| SSE 알림 누락 | SSE 실행기 큐와 경고 로그 | 알림 목록·채팅 이력 재조회, 연결 상태 |
| 지표만 안 보임 | 앱 `up`, DB Exporter `up` | Alloy 상태, 관리 포트와 Exporter |
| 로그만 안 보임 | Loki 시간 범위 | JSON 로그 파일, Alloy 파일 수집 상태 |
| 트레이스만 안 보임 | Tempo 시간 범위와 샘플링 | Alloy OTLP 수신, 앱 trace 내보내기 설정 |

대시보드의 p95는 실제 API 요청이 없으면 비어 있고, 5xx 비율은
요청이 거의 없으면 의미가 약할 수 있습니다. 현재 p95 경고는
**5분간 해당 route 요청 100건 이상**이며 p95가
**1초 초과**인 상태가 **5분간 지속**될 때, 5xx 경고는 별도 요청량 조건과
지속 시간을 만족할 때 발동합니다. 실제 조건은
[알림 규칙](../deploy/monitoring/prometheus/alerts.yml)을 확인합니다.
로컬 구성에는 cAdvisor가 없어 **Container CPU (Production)**와 **Container Memory (Production)**
패널이 비어 있습니다. 두 패널은 운영 EC2에서 cAdvisor를 켰을 때 사용합니다.

알림 규칙은 Prometheus가 평가하고 Alertmanager가 수신합니다. 상태는
Grafana **Alerting**이나 [Prometheus Alerts](http://localhost:9090/alerts)에서
볼 수 있습니다.
Alertmanager의 `discord` receiver는 비밀 파일에 저장된 Discord 웹훅으로
알림 발생·해제를 전송합니다. 웹훅 파일을 설정하고 Alertmanager를 재시작한 뒤
발생·해제 알림을 각각 확인하세요. 설정 방법은
[모니터링 서버 안내](../deploy/monitoring/README.md)에 있습니다.
운영 장애 대응 순서는 [모니터링 런북](../deploy/monitoring/RUNBOOK.md)에
있습니다.

참고 문서: [Grafana Explore](https://grafana.com/docs/grafana/latest/visualizations/explore/),
[Loki 로그 조회](https://grafana.com/docs/grafana/latest/visualizations/explore/logs-integration/),
[Tempo 검색](https://grafana.com/docs/grafana/latest/datasources/tempo/query-editor/traceql-search/).
