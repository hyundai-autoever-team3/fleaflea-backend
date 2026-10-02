# Fleaflea 모니터링 화면 읽기 가이드

로컬 모니터링을 실행한 뒤 Grafana에서 DB, JVM, SSE 큐, 외부 상태 검사,
로그와 트레이스를 확인하는 방법입니다. 실행·로그인·종료 절차는
[로컬 모니터링 사용 가이드](LOCAL_MONITORING.md)를 참고합니다.

## 먼저 화면 열기

1. [Grafana](http://localhost:3000)에 로그인합니다.
2. **Dashboards → Fleaflea → Fleaflea Overview**를 엽니다.
3. 오른쪽 위 시간 범위를 **Last 15 minutes**로 바꿉니다. 과거 데이터가
   필요하면 범위를 넓힙니다.
4. 상세 지표나 로그는 왼쪽 메뉴 **Explore**에서 데이터 소스를
   **Prometheus**, **Loki**, **Tempo** 중 하나로 선택해 조회합니다.
   Prometheus 쿼리는 Code 모드에 붙여 넣고 **Run query**를 누릅니다.
   코드 블록에 쿼리가 여러 줄 있으면 한 줄씩 별도로 실행합니다.

숫자 `0`은 값이 수집됐지만 해당 현상이 없다는 뜻입니다. **No data**는
조회 기간에 시계열이나 로그가 없다는 뜻이므로 정상이라는 증거가 아닙니다.
막 시작한 앱, 발생한 적 없는 오류, 요청량이 적은 구간에서도 No data가
나올 수 있습니다. 먼저 시간 범위와 아래 `up` 지표를 확인합니다.

쿼리의 `service="fleaflea"`는 애플리케이션을 고릅니다. 로컬과 운영 데이터가
같은 Prometheus에 들어온다면 `environment="local"` 또는
`environment="production"` 조건을 추가하고, 인스턴스별 차이를 볼 때는
`instance` 라벨로 좁힙니다.

## 1. 수집 상태와 외부 상태 검사

대시보드의 **Availability and collection** 패널을 먼저 봅니다.
Prometheus Explore에서 다음 쿼리를 각각 실행할 수도 있습니다.

```promql
up{job="fleaflea-app"}
up{job="fleaflea-postgres"}
probe_success{job="blackbox-http",service="fleaflea"}
```

| 값 | 의미 |
| --- | --- |
| 앱 `up=1` | Alloy가 `/actuator/prometheus`를 읽어 지표를 전송 중 |
| DB `up=1` | PostgreSQL Exporter 지표를 수집 중 |
| `probe_success=1` | Blackbox가 외부 `/readyz`에서 성공 응답을 받음 |

`up=0`은 지표 수집 실패, `probe_success=0`은 대상 검사 실패입니다. 둘은
서로 다른 신호입니다. Blackbox의 HTTP 상태 코드와 검사 시간은 다음처럼 봅니다.

```promql
probe_http_status_code{job="blackbox-http",service="fleaflea"}
probe_duration_seconds{job="blackbox-http",service="fleaflea"}
```

로컬 Blackbox 대상은 `http://host.docker.internal:8080/readyz`, 운영 대상은
`https://api.fleaflea.app/readyz`입니다. `probe_success=0`이면 상태 코드와
앱·DB `up`을 비교합니다. `probe_success=0`인데 앱 `up=1`이면 외부 경로,
앱 준비 상태, DB 상태를 차례로 확인합니다. 앱 `up` 자체가 없다면 Alloy와
앱의 관리 포트부터 확인합니다.

## 2. DB 지표: 앱 연결 풀과 PostgreSQL 구분하기

대시보드의 **Database pool** 패널은 앱이 사용하는 HikariCP 연결 풀입니다.
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

PostgreSQL 서버 쪽 지표는 대시보드에 별도 패널이 없으므로 **Prometheus
Explore**에서 조회합니다. `datname`을 지정하지 않으면 템플릿 DB까지
함께 표시됩니다.

```promql
pg_stat_database_numbackends{datname="fleaflea_db"}
pg_stat_activity_count{datname="fleaflea_db",state="idle in transaction"}
increase(pg_stat_database_deadlocks{datname="fleaflea_db"}[1h])
```

순서대로 현재 DB 연결 수, 트랜잭션을 열어둔 채 유휴 상태인 연결 수,
최근 1시간의 교착 증가량입니다. PostgreSQL 연결 수에는 앱 외의 연결도
포함되므로 HikariCP `active`와 같을 필요는 없습니다. `deadlocks`는 누적
카운터이므로 현재 원시값보다 `increase`로 최근 증가 여부를 봅니다.

## 3. JVM 힙과 SSE 작업 큐

대시보드의 **JVM heap** 패널에서 `used`와 `max` 추세를 비교합니다.
단위는 바이트이며 Grafana가 읽기 쉬운 단위로 표시합니다. Explore 쿼리는
다음과 같습니다.

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
대시보드 **SSE health** 패널은 연결 수와 작업 실패·거부를 보여주며,
큐 자체는 Prometheus Explore에서 확인합니다.

```promql
executor_queued_tasks{service="fleaflea",name="notificationSseExecutor"}
executor_active_threads{service="fleaflea",name="notificationSseExecutor"}
executor_pool_max_threads{service="fleaflea",name="notificationSseExecutor"}
increase(fleaflea_sse_tasks_total{service="fleaflea",outcome="rejected"}[5m])
increase(fleaflea_sse_tasks_total{service="fleaflea",outcome="failed"}[5m])
```

`queued`가 쌓이고 `active`가 늘어나는 상태가 계속되면 처리 속도가
유입 속도를 못 따라가는지 확인합니다. `rejected`가 증가하면 실제 작업이
실행되지 못한 것이며 현재 알림 규칙은 한 건만 있어도 경고합니다.
`type` 라벨로 `notification`, `chat`, `heartbeat`를 구분할 수 있습니다.
하트비트 작업은 SSE 연결이 없어도 30초마다 제출되므로 제출 건수만으로
알림 전송량을 판단하지 않습니다.

작업의 `completed`와 실제 SSE 전송 성공은 다릅니다. 전송 실패는 별도
지표로 확인합니다. 전송 시도가 없었다면 이 시계열은 없을 수 있습니다.

```promql
increase(fleaflea_sse_sends_total{service="fleaflea",outcome="failure"}[5m])
fleaflea_sse_connections{service="fleaflea"}
```

## 4. Loki에서 로그 읽기

**Explore → Loki**를 선택하고 시간 범위를 맞춘 뒤 아래 LogQL을 실행합니다.
대시보드의 **Application logs** 패널에서도 최근 앱 로그를 볼 수 있습니다.

```logql
{service="fleaflea"} | json
```

필요한 로그만 좁힙니다.

```logql
{service="fleaflea"} | json | log_level="ERROR"
{service="fleaflea"} | json | status >= 500
{service="fleaflea"} | json | errorCode="INTERNAL_SERVER_ERROR"
{service="fleaflea"} | json | traceId="여기에_traceId"
```

로컬 앱은 `build/logs/fleaflea.json`의 ECS JSON 로그를 Alloy가 읽습니다.
한 로그 줄을 펼치면 `message`, `route`, `method`, `status`, `durationMs`,
`errorCode`, `traceId`, `spanId`를 확인할 수 있습니다. `401`·`405`처럼
의도적으로 보낸 테스트 요청도 로그에 남으므로 상태 코드와 요청 경로를
함께 판단합니다. 로그가 안 보이면 **시간 범위 → 앱 JSON 파일 → Alloy →
Loki** 순서로 점검합니다.

## 5. Tempo에서 요청 흐름 보기

**Explore → Tempo → Search**에서 Service Name을 `fleaflea`로 고르고
시간 범위를 맞춰 검색합니다. 결과에서 요청을 열어 전체 시간과 각 span의
소요 시간을 비교합니다. Loki 로그에 `traceId`가 있으면 Tempo의 Trace ID
조회에 붙여 넣거나 로그 상세의 **TraceID** 링크를 사용합니다. Tempo에서
**Logs for this span**을 누르면 같은 trace의 Loki 로그로 돌아갈 수 있습니다.

이미지 작업에는 `fleaflea.image.validation`과 `fleaflea.image.s3`, SSE 작업에는
`fleaflea.sse.task` span이 표시될 수 있습니다. 해당 작업을 실행하지
않았다면 관련 span도 없습니다. 로컬 가이드의 추적 샘플링은 100%지만,
운영 기본값은 10%입니다. 따라서 운영 로그에 `traceId`가 있어도 Tempo에
trace가 없을 수 있습니다. Tempo 검색 결과가 없을 때는 먼저 시간 범위,
요청 발생 여부, 샘플링과 Alloy의 OTLP 수신 상태를 확인합니다.

## 증상에서 시작하는 조회 순서

| 증상 | 먼저 볼 것 | 다음 확인 |
| --- | --- | --- |
| 서비스 접속 불가 | `probe_success`, 앱 `up`, `/readyz` | `probe_http_status_code`, DB `up`, 최근 로그 |
| DB 요청 지연 | Hikari `pending`·`active` | PostgreSQL 연결·교착, 느린 요청의 trace |
| 요청이 느림 | Route latency p95와 요청 수 | JVM 힙·GC, Hikari `pending`, Tempo span |
| SSE 알림 누락 | `rejected`·`failed`, 큐 길이 | `fleaflea_sse_sends_total`, 로그, 연결 수 |
| 지표만 안 보임 | 앱 `up`, DB `up` | Alloy 상태, 관리 포트와 Exporter |
| 로그만 안 보임 | Loki 시간 범위 | JSON 로그 파일, Alloy 파일 수집 상태 |
| 트레이스만 안 보임 | Tempo 시간 범위와 샘플링 | Alloy OTLP 수신, 앱 trace 내보내기 설정 |

대시보드의 p95·5xx 비율은 요청이 거의 없으면 비어 있거나 의미가 약할 수
있습니다. 현재 p95 경고는 **5분간 해당 route 요청 100건 이상**이며 p95가
**1초 초과**인 상태가 **5분간 지속**될 때, 5xx 경고는 별도 요청량 조건과
지속 시간을 만족할 때 발동합니다. 실제 조건은
[알림 규칙](../deploy/monitoring/prometheus/alerts.yml)을 확인합니다.
로컬에는 cAdvisor가 없어 Container CPU/Memory 패널이 비어 있습니다.

알림 상태는 Grafana **Alerting**이나
[Prometheus Alerts](http://localhost:9090/alerts)에서 볼 수 있습니다.
운영 장애 대응 순서는 [모니터링 런북](../deploy/monitoring/RUNBOOK.md)에
있습니다.

참고 문서: [Grafana Explore](https://grafana.com/docs/grafana/latest/visualizations/explore/),
[Loki 로그 조회](https://grafana.com/docs/grafana/latest/visualizations/explore/logs-integration/),
[Tempo 검색](https://grafana.com/docs/grafana/latest/datasources/tempo/query-editor/traceql-search/).
