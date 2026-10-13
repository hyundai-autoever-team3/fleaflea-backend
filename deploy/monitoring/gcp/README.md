# GCP 수집기 → 로컬 Grafana

이 Compose는 `fleaflea` 앱 배포와 별도인 `fleaflea-observer` 프로젝트로 실행합니다.
이 수집기 실행만으로 기존 앱 컨테이너나 방화벽을 변경하지 않습니다.
앱의 관리 포트 분리는 별도의 앱 이미지·배포 설정 반영이 필요합니다.
Alloy는 호스트 네트워크로 SSH 역방향 터널의 loopback에 연결합니다.
서버·Redis exporter는 Alloy에 내장돼 있고 PostgreSQL exporter·cAdvisor는 별도 컨테이너입니다.
Docker API는 전용 내부 Docker 네트워크 `172.29.250.0/28`의 읽기 전용
socket proxy(`172.29.250.2:2375`)를 통해 접근하며 호스트 포트를 공개하지 않습니다.
서버의 기존 Docker·VPC·VPN 네트워크와 서브넷이 겹치지 않는지 설치 전 확인합니다.
cAdvisor는 컨테이너 자원을 읽기 위해 privileged로 실행합니다.

## 설치

서버 `/opt/fleaflea-observer`에 `compose.yml`과 `config.alloy`를 설치합니다.
실행 전에 다음 환경 파일을 별도로 준비해야 합니다.

- `/etc/fleaflea/observer.env`: 환경·서버 식별자, Prometheus·Loki 전송 주소, OTLP 수신 주소와 PostgreSQL 모니터링 계정.
- `/etc/fleaflea/redis.env`: 기존 Redis 비밀번호.

두 파일은 root 소유·권한 `600`으로 관리하고 Git에 올리지 않습니다.
PostgreSQL 모니터링 계정에는 대상 DB의 `CONNECT`와 `pg_monitor` 권한이 필요합니다.
개인 설치 보조 스크립트를 저장소에 포함된 필수 파일로 가정하지 않습니다.

```bash
sudo docker compose -f /opt/fleaflea-observer/compose.yml up -d
```

로컬 저장소 실행과 SSH 역방향 터널은 [상위 README](../README.md)의 절차를 따릅니다.
수집기를 실행해도 앱 이미지를 배포하거나 SSH 터널을 자동으로 연결하지는 않습니다.

## 블루·그린

실행 중인 `fleaflea-blue`·`fleaflea-green`의 로그를 각각 수집합니다.
Docker Compose의 기본 프로젝트·서비스 라벨로 실행 중인 `app` 컨테이너를 탐색합니다.
추가 `fleaflea.metrics.port` 라벨 없이 컨테이너 내부 IP의 `8081/actuator/prometheus`를
수집하며 `slot`과 컨테이너 ID로 구분합니다. 중지한 앱은 10초 주기의 탐색 후 제거됩니다.
서버의 Blue `8081`·Green `8082`는 각각 컨테이너 내부 `8080`에 연결되는 호스트 포트입니다.
Alloy는 이 호스트 포트를 사용하지 않습니다.

앱 API는 내부 `8080`, Actuator는 별도 내부 `8081`을 사용합니다.
관리 포트는 호스트에 publish하지 않으며 Nginx upstream도 API 포트에만 연결합니다.
Alloy는 호스트 네트워크에서 컨테이너 내부 IP의 관리 포트에 직접 접근합니다.
GCP 앱 Compose는 `MANAGEMENT_PORT=8081`을 명시합니다.
로컬 Alloy와 GCP Alloy 모두 `8081`을 사용합니다.

앱이 내부 `8081/actuator/prometheus`에서 지표를 제공해야 합니다.
지표를 제공하지 않는 이전 이미지에서는 수집 실패(`up=0`)가 표시됩니다.
Docker 상태 확인, 배포 전·후 확인, GCP uptime check는 메인 서비스의 `/readyz`를 사용합니다.
이전 이미지로 롤백하려면 해당 이미지도 `/readyz`를 제공하는지 먼저 확인합니다.
Nginx는 API 포트에만 연결하며 별도의 `/actuator` 경로 차단 규칙은 사용하지 않습니다.
`deploy/gcp/nginx-proxy.conf`를 서버의 `/etc/nginx/fleaflea-proxy.conf`로 설치하고
`sudo nginx -t` 성공 후 `sudo systemctl reload nginx`로 반영합니다.
앱 CD는 GCP uptime check 설정을 갱신하지 않으므로, 신규 설치에서는 검사 경로를 `/readyz`로 별도 설정합니다.

## ECS 로그와 트레이스

- 앱 `/etc/fleaflea/fleaflea.env`에 다음 설정을 추가합니다.

```dotenv
OTEL_JAVAAGENT_ENABLED=true
OTEL_SERVICE_NAME=fleaflea
OTEL_RESOURCE_ATTRIBUTES=deployment.environment.name=production
LOG_FORMAT_CONSOLE=ecs
OTEL_TRACES_EXPORTER=otlp
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://<앱 Docker 네트워크 gateway>:4318/v1/traces
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_METRICS_EXPORTER=none
OTEL_LOGS_EXPORTER=none
```

- `docker network inspect fleaflea_default`에서 앱 네트워크의 gateway를 확인합니다.
- `/etc/fleaflea/observer.env`에 `OTLP_HTTP_LISTEN_ADDRESS=<gateway>:4318`을 추가합니다.
- Alloy는 해당 내부 주소에서 트레이스를 받아 SSH 터널 `127.0.0.1:14317`을 통해 로컬 Tempo로 전송합니다.
- 수신 주소를 생략하면 `127.0.0.1:4318`에 바인딩하며, Docker 앱에서는 접근할 수 없습니다.
- 외부 IP에 OTLP 포트를 공개하지 않습니다.
- 수집기는 `docker compose -f /opt/fleaflea-observer/compose.yml up -d --no-deps --force-recreate alloy`로 환경변수를 반영합니다.
- 앱은 블루·그린 배포로 재생성해야 변경된 환경변수가 반영됩니다. 단순 재시작은 반영하지 않습니다.
- Loki에서 HTTP 요청의 JSON 로그에 `trace_id`·`span_id`가 있는지 확인합니다. 시작 로그 등 트레이스 문맥이 없는 로그에는 ID가 없을 수 있습니다.

## 확인·중지

로컬 Prometheus에서 `node_memory_MemAvailable_bytes`, `pg_up`, `redis_up`,
`container_memory_usage_bytes`와 환경 `production`의 데이터를 확인합니다.
Grafana의 `GCP Server Overview` (`/d/fleaflea-gcp-server`)에서 이 서버 지표를 함께 조회합니다.
Loki에서 `{environment="production", service="fleaflea"}`로 앱 로그를 확인합니다.
수집기 중지는 `sudo docker compose -f /opt/fleaflea-observer/compose.yml stop`입니다.
DB 역할·환경 파일과 수집기 볼륨은 중지해도 보존됩니다.
SSH 연결이 종료되면 로컬 전송은 중단됩니다. 지표 WAL의 보관 한도는 8시간이며
장시간 미접속 시 모든 데이터를 보존하지는 않습니다.
