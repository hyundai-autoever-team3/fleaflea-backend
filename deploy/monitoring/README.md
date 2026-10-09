# Fleaflea 모니터링 서버

애플리케이션 EC2와 별도의 호스트에서 Fleaflea의 지표, 로그, 트레이스를 저장합니다.
Prometheus, Loki, Tempo, Grafana, Alertmanager, Blackbox Exporter, Node Exporter를 실행합니다.

애플리케이션 EC2 배포 방법은 [`deploy/ec2/README.md`](../ec2/README.md),
장애 대응 절차는 [모니터링 가이드 Wiki](https://github.com/hyundai-autoever-team3/fleaflea-backend/wiki/%EB%AA%A8%EB%8B%88%ED%84%B0%EB%A7%81-%EA%B0%80%EC%9D%B4%EB%93%9C)를 참고합니다.

## 로컬 개발

아래 명령은 저장소 루트에서 실행합니다. 명령 예시는 Bash 기준입니다.
Windows PowerShell에서는 Gradle 실행 시 `./gradlew` 대신 `.\gradlew.bat`을 사용합니다.

1. 로컬 PostgreSQL이 `5432` 포트에서 실행 중이고 `fleaflea_db`가 있는지 확인합니다.
   `.env`의 DB 계정과 `deploy/monitoring/.env.local`의
   `LOCAL_DB_USERNAME`, `LOCAL_DB_PASSWORD`를 동일하게 설정합니다.

   ```bash
   psql -h 127.0.0.1 -p 5432 -U postgres -d fleaflea_db -c 'SELECT 1'
   ```

   `performance/compose.yml`의 PostgreSQL은 별도 성능 테스트를 위해 호스트의
   `5435` 포트를 사용하므로 이 로컬 모니터링 구성에서는 사용하지 않습니다.

2. 로컬 모니터링 환경 파일을 만듭니다. 이미 파일이 있으면 덮어쓰지 않습니다.
   Grafana 비밀번호를 변경하고 로컬 접속 주소를 설정합니다.

   ```bash
   cp deploy/monitoring/.env.example deploy/monitoring/.env.local
   ```

   ```dotenv
   GRAFANA_ADMIN_PASSWORD=local-grafana-password
   GRAFANA_ROOT_URL=http://localhost:3000
   ```

   `deploy/monitoring/secrets/discord-webhook-url`에 Discord 웹훅 URL을 한 줄로
   저장하고, Unix 환경에서는 파일 권한을 `600`으로 설정합니다.
   이 파일은 Git에서 제외되며 Alertmanager 실행에 필요합니다.

3. 앱 로그 디렉터리를 만들고 로컬 Alloy 설정과 함께 모니터링 도구를 실행합니다.

   ```bash
   mkdir -p build/logs
   docker compose \
     --env-file deploy/monitoring/.env.local \
     -f deploy/monitoring/compose.yml \
     -f deploy/monitoring/compose.local.yml \
     up -d --wait
   ```

4. 루트에 `.env`가 없으면 `.env.example`을 복사하고 다음 관측 설정을 추가합니다.

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

5. 애플리케이션을 실행합니다.

   ```bash
   ./gradlew bootRun -PotelAgent
   ```

   IntelliJ의 Application 실행을 사용하면 먼저 `./gradlew prepareOtelAgent`를 실행한 뒤,
   VM options에 `-javaagent:"$PROJECT_DIR$/build/otel/opentelemetry-javaagent.jar"`를 넣습니다.
   실행 구성의 환경변수에도 위 `OTEL_*` 값과 앱 설정을 넣어야 합니다.
   Gradle 실행과 달리 일반 Application 실행은 루트 `.env`를 자동으로 읽지 않습니다.

   `LOG_FORMAT_CONSOLE`을 생략하면 일반 텍스트 콘솔에 trace ID와 span ID가 표시됩니다.
   요청과 무관한 로그에는 ID가 비어 있을 수 있습니다.
   `LOG_FORMAT_CONSOLE=ecs`를 지정하면 콘솔도 JSON 형식으로 출력합니다.

`http://localhost:3000`에서 Grafana에 접속해 **Fleaflea Overview**를 엽니다.
서버 지표는 **Fleaflea Host Details**, DB 지표는 **Fleaflea PostgreSQL Details**에서 확인합니다.
HTTP 접속 상태와 지연은 **Blackbox Exporter (HTTP prober)**에서 확인합니다.

서버 대시보드는 [Node Exporter Full, ID 1860](https://grafana.com/grafana/dashboards/1860-node-exporter-full/)을
현재 데이터 소스에 맞춰 사용합니다. DB 대시보드는 이 구성에서 실제 수집하는 지표를 사용합니다.
로컬 Node Exporter는 Windows·macOS 호스트가 아닌 Docker Desktop의 Linux VM을 측정합니다.

대시보드 제목, 패널명, 범례는 영어로, Fleaflea에서 작성한 패널 설명은 한국어로 표시합니다.

대시보드는 `grafana/dashboards/`의 JSON 파일에서 자동으로 등록됩니다.
Grafana는 약 30초마다 파일을 다시 읽으며 화면에서 직접 수정하는 기능은 비활성화되어 있습니다.
패널을 변경하려면 JSON 파일을 수정합니다.

Prometheus는 `http://localhost:9090`, Loki는 `3100` 포트, Tempo 조회 API는 `3200` 포트,
로컬 Alloy UI는 `http://localhost:12346`에서 확인합니다.

로컬 Alloy는 앱의 `8081` 포트에서 지표를 수집하고, `4318` 포트에서 OTLP/HTTP 트레이스를
받으며, `build/logs/*.json` 로그 파일을 읽습니다. PostgreSQL Exporter는 로컬 DB의
`5432` 포트에 연결하며 Alloy가 해당 Exporter의 지표를 수집합니다.
DB는 Docker의 `host.docker.internal`을 통한 연결을 허용해야 합니다.

저장 볼륨을 보존하면서 로컬 모니터링 도구를 종료합니다.

```bash
docker compose \
  --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/compose.local.yml \
  down
```

## 네트워크 설정

수집 데이터의 전송 주소에는 모니터링 서버의 사설 주소를 사용합니다.
다음 포트는 애플리케이션 EC2의 보안 그룹에서만 접근할 수 있도록 허용합니다.

- `9090/tcp`: Prometheus remote write
- `3100/tcp`: Loki push API
- `4317/tcp`: Tempo OTLP gRPC

Grafana는 `127.0.0.1:3000`에 바인딩하고 인증이 적용된 리버스 프록시, VPN,
또는 SSM 포트 포워딩을 통해 접속합니다. Prometheus, Loki, Tempo, Alertmanager를
인터넷에 공개하지 않습니다. 현재 수집 엔드포인트에는 애플리케이션 수준의 인증이 없으므로
네트워크 접근 제한이 필요합니다.

## 운영 모니터링 서버 실행

모니터링 구성 파일이 `/opt/fleaflea-monitoring`에 설치된 상태에서 실행합니다.
이미 `.env`가 있으면 예제 파일로 덮어쓰지 않습니다.

```bash
cd /opt/fleaflea-monitoring
cp .env.example .env
chmod 600 .env
# 비밀번호, 접속 주소, 바인딩 주소, 데이터 보관 기간과 용량을 설정합니다.
docker compose config --quiet
docker compose up -d --wait
```

운영 `.env`의 `TELEMETRY_BIND_ADDRESS`에는 일반적으로 모니터링 서버의 사설 IP를 사용합니다.
실행 전에 해당 주소와 수집용 포트 세 개의 보안 그룹 접근 범위를 제한합니다.

애플리케이션 EC2에는 `deploy/ec2/monitoring.env.example`을
`/etc/fleaflea/monitoring.env`로 복사하고 예시 사설 주소와 DB 계정을 실제 값으로 변경한 뒤
다시 배포합니다. 이 파일이 있으면 배포 스크립트가 Compose의 `monitoring` 프로필을 활성화합니다.
파일이 없으면 앱의 트레이스 전송이 비활성화됩니다.
기존 모니터링 컨테이너의 종료 여부는 별도로 확인합니다.

## 외부 준비 상태 검사

공개 호스트 이름이 바뀌면 `prometheus/targets/blackbox.yml`을 수정합니다.
로컬 override를 사용하면 `local/prometheus/blackbox.yml`을 수정합니다.
Blackbox는 앱 준비 상태와 PostgreSQL 연결을 포함하는 `/readyz`를 검사합니다.
관리 포트의 health 엔드포인트만으로 외부 서비스가 정상이라고 판단하지 않습니다.

## 알림 전송

Alertmanager의 `discord` receiver는 Docker Compose secret에서 웹훅 URL을 읽습니다.
실행 전에 이 디렉터리를 기준으로 `secrets/discord-webhook-url` 파일에 Discord 웹훅 URL을
한 줄로 저장하고 `chmod 600 secrets/discord-webhook-url`로 권한을 제한합니다.
`secrets/` 디렉터리는 Git에서 제외되며 파일이 없으면 Alertmanager가 시작되지 않습니다.
운영 서버에서는 비밀값 관리 절차나 배포 절차를 통해 해당 파일을 준비합니다.

Alertmanager를 재시작한 뒤 검증용 알림을 발생시키고 Discord에서 발생·해제 알림을
모두 확인합니다.

Grafana에는 Alertmanager가 데이터 소스로 등록됩니다. Discord 전송 여부와 별개로
알림 상태를 확인할 수 있지만 Alertmanager 자체가 정상 실행되어 있어야 합니다.
Prometheus의 `/alerts` 화면에서도 알림 규칙의 상태를 확인할 수 있습니다.

## 정상 동작 확인

```bash
docker compose ps
curl -fsS http://127.0.0.1:9090/-/ready
curl -fsS http://127.0.0.1:3100/ready
curl -fsS http://127.0.0.1:3200/ready
curl -fsS http://127.0.0.1:3000/api/health
```

Prometheus에서 `up{job="fleaflea-app"} == 1`인지 확인합니다.
Grafana의 **Fleaflea / Fleaflea Overview**에서 앱 JSON 로그를 찾고,
`trace_id` 링크로 Tempo에 이동한 뒤 트레이스의 로그 링크를 통해 Loki로 돌아오는지 확인합니다.
운영 트레이스는 일부 요청만 수집하므로 모든 로그에 대응하는 트레이스가 존재하지는 않습니다.

`docker compose down`과 재시작 후에도 named volume에 데이터가 유지됩니다.
일반적인 종료·재시작 작업에서 `docker compose down -v`를 실행하지 않습니다.
설정한 보관 기간과 용량 제한에 의존하기 전에 검증 환경에서 데이터 정리와 디스크 알림을 확인합니다.
