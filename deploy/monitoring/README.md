# GCP·로컬 앱 → 로컬 Grafana

GCP의 별도 수집기가 서버·PostgreSQL·Redis·컨테이너 지표와 앱 로그를 수집하고,
SSH 역방향 터널을 통해 로컬 Prometheus·Loki로 전송합니다.
서버 수집기 설치와 환경 파일은 [GCP 수집기 안내](gcp/README.md)를 참고합니다.
기존 GCP 앱 배포 설정은 `deploy/gcp/`에서 관리합니다.

## 로컬 실행

저장소 루트에서 실행합니다. 기존 `.env.local`이 있으면 유지합니다.
새로 만드는 경우에만 다음 파일을 복사하고 Grafana 비밀번호를 변경합니다.
실행 전에 `deploy/monitoring/secrets/discord-webhook-url`에 유효한 Discord 웹훅 URL을
한 줄로 저장해야 합니다. 기존 파일이 있으면 유지하며 파일 권한은 `600`으로 설정합니다.

```bash
cp deploy/monitoring/.env.example deploy/monitoring/.env.local
chmod 600 deploy/monitoring/.env.local
```

```bash
docker compose --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml up -d
```

Grafana는 [localhost:3000](http://localhost:3000)에서 접속합니다.
**GCP Server Overview** 대시보드에서 서버와 DB·Redis·컨테이너 지표를 확인합니다.
앱 로그는 Loki에서 `{environment="production", service="fleaflea"}`로 조회합니다.

## GCP 연결

Google Cloud CLI 로그인과 VM의 IAP SSH 접속 권한이 필요합니다.
별도 터미널에서 실제 대상 값을 환경변수로 설정한 뒤 연결을 유지합니다.
아래 명령은 저장소에 포함되지 않는 로컬 전용 스크립트 없이 사용할 수 있습니다.

```bash
export GCP_PROJECT_ID=your-gcp-project-id
export GCP_INSTANCE=your-vm-name
export GCP_ZONE=your-vm-zone

gcloud compute ssh "$GCP_INSTANCE" \
  --project="$GCP_PROJECT_ID" --zone="$GCP_ZONE" \
  --tunnel-through-iap -- \
  -N -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=30 -o ServerAliveCountMax=3 \
  -R 127.0.0.1:19090:127.0.0.1:9090 \
  -R 127.0.0.1:13100:127.0.0.1:3100 \
  -R 127.0.0.1:14317:127.0.0.1:4317
```
이미 터널이 실행 중이면 새로 실행할 필요가 없습니다.

| 데이터 | GCP 터널 주소 | 로컬 수신 포트 |
|---|---|---|
| 지표 | `127.0.0.1:19090` | Prometheus `9090` |
| 로그 | `127.0.0.1:13100` | Loki `3100` |
| 트레이스용 연결 | `127.0.0.1:14317` | Tempo `4317` |

모든 로컬 포트는 loopback에만 열립니다.
GCP 수집기는 서버·DB·Redis·컨테이너 지표와 Spring 지표·앱 로그를 전송합니다.
GCP Alloy에는 트레이스 수신·전송 설정이 없으므로 터널 포트만 연결해도 GCP 트레이스가 수집되지는 않습니다.
로컬 앱 트레이스는 아래 OpenTelemetry 에이전트와 로컬 Alloy 설정으로 수집합니다.
Alertmanager는 로컬·GCP의 발생·복구 알림을 Discord로 전송합니다.
`deploy/monitoring/secrets/discord-webhook-url`에 Discord 웹훅 URL만 한 줄로 저장합니다.
파일은 Git에서 제외되며, 로컬 파일 권한은 `600`으로 유지합니다.

## 환경 파일

- 로컬 `deploy/monitoring/.env.local`: Grafana 관리자 계정과 Prometheus 보관 설정.
- GCP `/etc/fleaflea/observer.env`: 전송 주소·서버 식별자·전용 PostgreSQL 모니터링 계정.
- GCP `/etc/fleaflea/redis.env`: 기존 Redis 비밀번호를 수집기도 사용합니다.

비밀번호가 있는 환경 파일은 Git에 올리지 않습니다.
컴퓨터가 꺼지거나 터널이 끊기면 로컬 전송이 중단됩니다.
수집기의 지표 WAL 보관 한도는 8시간이며 장시간 미접속 시 데이터가 유실될 수 있습니다.

## 중지

```bash
docker compose --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/local/compose.yml stop
```

저장 볼륨은 유지됩니다. 기본 구성만 실행했다면 중지 명령에서도 `local/compose.yml`을 생략합니다.

## 내 로컬 앱도 수집하기

GCP 연결과 함께 로컬 Spring·PostgreSQL의 지표, 앱 로그와 트레이스를
같은 Grafana에서 볼 수 있습니다. `environment`가 `local`과 `production`으로 구분됩니다.
로컬 앱 지표는 **Fleaflea Overview**에서 환경을 `local`로 선택합니다.

로컬 PostgreSQL은 `5432`, 앱은 `8080`, Actuator 지표는 별도 `8081` 포트를 사용합니다.
`.env.local`에 실제 로컬 DB 계정을 설정합니다.

```dotenv
LOCAL_DB_USERNAME=postgres
LOCAL_DB_PASSWORD=your-local-db-password
```

```bash
mkdir -p build/logs
docker compose --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/local/compose.yml up -d
```

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

## 외부 접속 확인

Blackbox는 `local/compose.yml`을 함께 실행할 때 활성화됩니다.
기본 `compose.yml`만 실행하면 외부 접속 검사를 수행하지 않습니다.
로컬 Blackbox는 로컬 앱과 GCP의 공개 `https://api.fleaflea.app/readyz`를 검사합니다.
`environment`로 대상을, `probe_location=local`로 검사 위치를 구분합니다.
Grafana Overview와 GCP Server Overview에서 접속 상태를 확인하고,
상단 GCP Uptime Check 링크에서 해당 프로젝트의 상시 외부 검사 결과도 확인합니다.
로컬 검사가 3분 동안 실패하면 Discord에 경고를 보내며 서비스 전체 장애로 단정하지 않습니다.
노트북·로컬 인터넷 상태의 영향을 받을 수 있으므로 GCP Uptime Check를 운영 장애 판단의 기준으로 유지합니다.
