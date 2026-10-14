# 로컬 앱 / GCP 모니터링 분리

- 로컬 앱: Grafana **http://localhost:3000**
- GCP 앱·서버: Grafana **http://localhost:3001**
- 두 스택은 Prometheus·Loki·Tempo·Alertmanager·Grafana의 네트워크와 볼륨을 분리합니다.
- 각 Grafana에는 해당 스택의 데이터 소스만 연결됩니다.
- 팀원 공유는 GCP Grafana에만 연결합니다. 대시보드 필터를 접근 제어로 사용하지 않습니다.

## 환경 파일

기존 `deploy/monitoring/.env.local`은 유지합니다. 신규 설치 시에만 예시를 복사하고
Grafana 비밀번호와 로컬 DB 계정 및 GCP SSH 설정을 입력합니다.
Discord 알림은 환경별 파일에 웹훅 URL을 한 줄로 입력합니다.

- 로컬 앱 알림: `deploy/monitoring/secrets/discord-webhook-url-local`
- GCP 앱·서버 알림: `deploy/monitoring/secrets/discord-webhook-url-gcp`

서로 다른 채널로 보내려면 두 파일에 각각 해당 채널의 웹훅 URL을 입력합니다.
두 Alertmanager는 같은 컨테이너 경로 `/run/secrets/discord_webhook_url`로 각 파일을 읽습니다.
GCP 알림용 Alertmanager도 로컬 PC에서 실행됩니다.
알림의 대시보드 주소는 `.env.local`의 `LOCAL_GRAFANA_URL`·`GCP_GRAFANA_URL`로 설정합니다.
주소 끝의 `/`는 생략하며, 팀원 공유 시 `GCP_GRAFANA_URL`에 Tailscale HTTPS 주소를 입력합니다.
각 Alertmanager가 시작할 때 주소를 반영한 임시 템플릿을 생성합니다.
주소 변경 후에는 해당 스택의 `up -d alertmanager`를 다시 실행합니다.
환경 파일과 웹훅은 Git에 올리지 않으며 권한 `600`으로 관리합니다.

```bash
cp deploy/monitoring/.env.example deploy/monitoring/.env.local
chmod 600 deploy/monitoring/.env.local
mkdir -p deploy/monitoring/secrets
touch deploy/monitoring/secrets/discord-webhook-url-local deploy/monitoring/secrets/discord-webhook-url-gcp
chmod 600 deploy/monitoring/secrets/discord-webhook-url-local deploy/monitoring/secrets/discord-webhook-url-gcp
```

## 로컬 앱 스택 실행

```bash
mkdir -p build/logs
docker compose --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/common/compose.yml \
  -f deploy/monitoring/local/compose.yml up -d
```

- 프로젝트: `fleaflea-monitoring-local`
- 지표: `http://localhost:9090`
- 로그: `http://localhost:3100`
- 트레이스 조회: `http://localhost:3200`
- 앱 OTLP HTTP: `http://localhost:4318/v1/traces`
- Blackbox: 내 로컬 앱의 `http://host.docker.internal:8080/readyz` 검사
- GCP 전용 수집 장애 규칙은 로컬 스택에서 평가하지 않습니다.

로컬 앱 실행 환경에는 다음 값을 넣습니다.

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
LOGGING_FILE_NAME=build/logs/fleaflea.json
LOGGING_STRUCTURED_FORMAT_FILE=ecs
```

트레이스를 수집하려면 앱을 OpenTelemetry 에이전트와 실행합니다.

```bash
./gradlew bootRun -PotelAgent
```

IntelliJ Application 실행은 먼저 `./gradlew prepareOtelAgent`를 실행하고
VM options에 `-javaagent:"$PROJECT_DIR$/build/otel/opentelemetry-javaagent.jar"`를 설정합니다.
일반 Application 실행은 루트 `.env`를 자동으로 읽지 않으므로 실행 구성에 환경변수도 넣습니다.
로컬 Alloy는 `8081/actuator/prometheus`를 수집하고, Blackbox는 `8080/readyz`를 확인합니다.
로컬 앱 수집기를 실행해도 GCP 수집기나 GCP 앱 배포 설정은 변경되지 않습니다.

## GCP 스택 실행

```bash
docker compose --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/common/compose.yml \
  -f deploy/monitoring/gcp/compose.yml up -d
```

- 프로젝트: `fleaflea-monitoring-gcp`
- 서버 수집기: [GCP 수집기 안내](gcp/collector/README.md)
- Blackbox: GCP 공개 `/readyz` 검사
- Grafana의 GCP Server Overview에서 서버·DB·Redis·컨테이너 상태 조회
- Loki의 `{environment="production", service="fleaflea"}`로 운영 로그 조회
- 앱 Java 에이전트 → GCP Alloy → SSH 터널 → GCP Tempo로 트레이스 전송

## Tailscale 공유 설정을 유지하며 실행

개인 공유 설정이 있는 PC에서는 일반 GCP 실행 명령 대신 다음 조합을 사용합니다.
개인 override를 생략하면 Grafana의 HTTPS 주소와 Live 허용 주소 설정이 빠집니다.
이 파일에는 개인 주소와 허용 계정이 있으므로 Git에 포함하지 않습니다.

```bash
docker compose --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/common/compose.yml \
  -f deploy/monitoring/gcp/compose.yml \
  -f deploy/monitoring/.env.tailscale/compose.yml up -d
tailscale serve --bg http://127.0.0.1:13000
```

## GCP SSH 연결

별도 터미널에서 실제 대상 값을 설정하고 아래 연결을 유지합니다.
기존 터널은 종료한 뒤 새 포트로 다시 연결해야 합니다.

```bash
export GCP_PROJECT_ID=your-gcp-project-id
export GCP_INSTANCE=your-vm-name
export GCP_ZONE=your-vm-zone

gcloud compute ssh "$GCP_INSTANCE" \
  --project="$GCP_PROJECT_ID" --zone="$GCP_ZONE" \
  --tunnel-through-iap -- \
  -N -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=30 -o ServerAliveCountMax=3 \
  -R 127.0.0.1:19090:127.0.0.1:9091 \
  -R 127.0.0.1:13100:127.0.0.1:3101 \
  -R 127.0.0.1:14317:127.0.0.1:14317
```

| 데이터 | GCP 수집기 터널 주소 | 내 PC의 GCP 저장소 포트 |
|---|---|---|
| 지표 | `127.0.0.1:19090` | `9091` |
| 로그 | `127.0.0.1:13100` | `3101` |
| 트레이스 | `127.0.0.1:14317` | `14317` |

GCP의 기존 `/etc/fleaflea/observer.env` 전송 주소는 그대로 사용합니다.
모든 PC의 수신 포트는 loopback에만 열립니다.
PC나 SSH 연결이 종료되면 운영 데이터 전송도 중단됩니다.
GCP 지표 WAL 보관 한도는 8시간이며 트레이스는 장기 보관을 보장하지 않습니다.

## 외부 접속과 알림

- GCP 스택의 Blackbox는 로컬 인터넷에서 운영 `/readyz`를 검사합니다.
- 검사 실패는 접속 확인 실패로 표시하고 GCP Uptime Check를 함께 확인합니다.
- 각 스택의 Alertmanager가 해당 스택의 Discord 알림을 처리합니다.

## 기존 데이터와 공유

기존 `fleaflea-monitoring_*` 볼륨은 로컬 스택에서 재사용하여 데이터를 보존합니다.
이 볼륨에는 분리 이전의 로컬·GCP 데이터가 섞여 있을 수 있습니다.
GCP 스택은 별도 `fleaflea-monitoring-gcp_*` 볼륨으로 시작하며 과거 혼합 데이터를 복사하지 않습니다.
Tailscale 등 팀원 공유는 GCP 스택에만 연결합니다.
팀원에게는 Viewer 계정을 부여하며 저장소 접근 설정을 바꾸는 Admin 권한은 부여하지 않습니다.

## 중지

실행할 때 사용한 Compose 파일 조합에 `stop`을 사용합니다.
`down -v`는 데이터를 삭제하므로 사용하지 않습니다.

## 폴더 구성

```text
monitoring/
├── README.md
├── .env.example
├── common/                       # 양쪽 환경에서 재사용하는 설정
│   ├── compose.yml               # 저장소·Grafana·알림 공통 서비스
│   ├── alloy/pipelines.alloy     # 트레이스 필터·수집기 상태 지표
│   ├── prometheus/
│   │   ├── prometheus.yml
│   │   ├── rules/alerts.yml      # 공통 알림 규칙
│   │   └── alerts.test.yml       # 기존 규칙 테스트
│   ├── alertmanager/alertmanager.yml
│   ├── blackbox/config.yml
│   ├── grafana/                  # 대시보드·프로비저닝
│   ├── loki/config.yml
│   └── tempo/config.yml
├── local/                        # PC에서 실행 · 로컬 앱 관측
│   ├── compose.yml
│   ├── alloy/config.alloy
│   └── blackbox/targets.yml
├── gcp/
│   ├── compose.yml               # PC에서 실행 · GCP 데이터 조회
│   ├── prometheus/rules/alerts.yml
│   ├── blackbox/targets.yml
│   └── collector/                # GCP 서버에서 실행
│       ├── README.md
│       ├── compose.yml
│       ├── alloy/config.alloy
│       └── docker-proxy/haproxy.cfg
└── secrets/                      # 개인 웹훅 · Git 제외
```

로컬·GCP의 실행 설정은 각 환경 폴더에 두고, 공통 설정 파일은 `common/`에서 한 번만 관리합니다.
실행 시 `common/compose.yml`을 먼저, 환경별 `compose.yml`을 다음에 지정합니다.
Compose의 상대 마운트 경로는 첫 번째 파일인 `common/compose.yml` 기준입니다.
환경별 중복 README는 이 가이드로 합쳤으며 서버 수집기 설치 절차만 별도 가이드로 유지합니다.
개인 `.env.local`·`.env.tailscale/`·GCP 설치 보조 파일은 기존 위치와 Git 제외 상태를 유지합니다.


Prometheus의 공통 규칙은 두 스택에서 사용하고 GCP 전용 규칙은 GCP 스택에만 마운트합니다.
각 스택의 저장소·네트워크·수집 대상은 서로 분리됩니다.

### 수집 역할

- Spring 지표는 각 환경의 Alloy에서 한 번 수집합니다. Prometheus는 앱을 별도로 중복 scrape하지 않습니다.
- GCP 호스트 지표와 cAdvisor 지표는 각각 서버 전체와 개별 컨테이너를 관측합니다.
- Blackbox와 GCP Uptime Check는 각각 내 PC와 외부 관리 서비스에서 접속을 확인합니다.
- 로컬 Alloy는 파일 로그와 로컬 앱을, GCP Alloy는 Docker 로그와 서버·컨테이너를 수집하므로 별도 구성으로 유지합니다.

### 알림과 로그·트레이스 처리

- API 오류율은 5분간 지속되는 경우 알림을 보냅니다.
- 최근 5분간 5xx 10건 이상 발생한 경우 별도 급증 알림이 1분 대기 후 발생합니다.
- 로컬·GCP Alloy의 전송 상태 지표를 수집하며 로그 재시도·폐기와 트레이스 전송 실패·큐 포화·enqueue 실패를 감시합니다.
- 전송 중단 알림은 재시도/실패가 있으면서 최근 5분간 성공한 전송이 없을 때 평가합니다.
- GCP cAdvisor 장애와 Alloy 자체 상태 지표 누락도 감시합니다.
- 로컬 ECS 로그는 `@timestamp`를 Loki 이벤트 시각으로 적용합니다. 파싱 실패 시 기존 수집 시각을 유지합니다.
- 로컬·GCP는 `common/alloy/pipelines.alloy`의 공통 정책으로 `/readyz`·Actuator 검사·SSE heartbeat·지정된 DB 유지 작업 트레이스를 제외합니다.
- 기존에 저장된 검사 트레이스는 보관 기간이 끝날 때까지 남습니다.
