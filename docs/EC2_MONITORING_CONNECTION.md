# EC2 앱·PostgreSQL을 중앙 모니터링에 연결하기

이 가이드는 **앱 EC2**에서 Docker Compose로 실행하는 Spring Boot와 PostgreSQL을
**별도 모니터링 EC2**의 Prometheus·Loki·Tempo·Grafana에 연결하는 절차입니다.
현재 저장소의 배포 구성을 기준으로 합니다. 실제 서버 주소와 비밀번호는 예시값을
사용하지 말고 운영 환경의 값으로 바꾸세요. 로컬 실행은
[로컬 모니터링 가이드](LOCAL_MONITORING.md)를 따릅니다.
지금 노트북에서 실행 중인 `localhost` 모니터링 스택은 앱 EC2의 목적지가
될 수 없습니다. 아래 절차는 Docker Engine·Compose가 준비된 모니터링 EC2를
앱 EC2에서 사설 IP로 접근할 수 있다는 전제입니다.

```text
앱 EC2 (deploy/ec2/compose.yml)
  Spring Boot :8081 ──────────────┐
  Node Exporter · cAdvisor ───────┼─→ Alloy ── 9090/tcp ─→ Prometheus
  PostgreSQL :5432 → Exporter ────┘       ├── 3100/tcp ─→ Loki (컨테이너·Nginx 로그)
  Spring Boot OTLP/HTTP :4318 ────────────└── 4317/tcp ─→ Tempo (트레이스)

모니터링 EC2 (deploy/monitoring/compose.yml)
  Prometheus · Loki · Tempo · Grafana · Alertmanager · Blackbox Exporter
```

앱 EC2의 `5432`, `8081`, `9187`, Alloy `4318`을 모니터링 EC2에 공개할 필요는
없습니다. 앱 EC2의 Alloy가 같은 Compose 네트워크에서 대상들을 수집하고,
모니터링 EC2의 사설 IP로 **전송**합니다. PostgreSQL Exporter는 앱 EC2의
Compose 네트워크에서 `postgres:5432`에 연결합니다.

## 1. 연결 전에 확인할 값

| 값 | 설정 위치 | 설명 |
| --- | --- | --- |
| 모니터링 EC2 사설 IP | 두 서버 | `TELEMETRY_BIND_ADDRESS`와 Alloy 전송 URL에 같은 실제 IP 사용 |
| 앱 EC2 보안 그룹 | 모니터링 EC2 보안 그룹 | 수신 포트의 허용 출처 |
| Grafana 접속 주소 | 모니터링 EC2 `.env` | SSM 터널이면 `http://localhost:3000`, 인증된 프록시를 쓰면 해당 URL |
| Discord 웹훅 | 모니터링 EC2 비밀 파일 | Alertmanager 시작에 필요하며 Git에 넣지 않음 |
| PostgreSQL 모니터링 계정 | 앱 EC2 DB와 `monitoring.env` | 앱 계정과 별도의 읽기 전용 수집 계정 |

두 EC2가 사설 IP로 통신할 수 있어야 합니다. 같은 VPC에 배치하는 구성이
가장 단순합니다. 모니터링 EC2 보안 그룹의 **인바운드**에서 아래 포트의
출처를 앱 EC2 보안 그룹으로 제한하고, 앱 EC2의 아웃바운드에서도 같은
목적지·포트가 허용되는지 확인합니다.
보안 그룹을 출처로 지정하는 규칙은 같은 VPC 등 AWS가 지원하는 네트워크
관계에서 사용할 수 있습니다. VPC가 다르면 라우팅과 보안 그룹 참조 가능
여부를 먼저 확인합니다.

| 모니터링 EC2 수신 포트 | 용도 |
| --- | --- |
| `9090/tcp` | Prometheus remote write |
| `3100/tcp` | Loki 로그 전송 |
| `4317/tcp` | Tempo OTLP/gRPC 전송 |

`3000`(Grafana), `9093`(Alertmanager), `3200`(Tempo 조회 API)는 현재 Compose에서
`127.0.0.1`에만 바인딩됩니다. Grafana는 SSM 포트 포워딩 또는 인증된
프록시로 접속하세요. DB·Actuator·Exporter 포트를 인터넷에 열지 않습니다.

## 2. 모니터링 EC2 준비

앱 배포 워크플로는 **모니터링 EC2의 파일을 배포하지 않습니다**. 저장소의
`deploy/monitoring/` 내용을 모니터링 EC2의 `/opt/fleaflea-monitoring/`에
별도로 복사해야 합니다. 예를 들어 저장소 루트에서 다음처럼 복사할 수
있습니다. `MONITOR_USER`와 `MONITOR_HOST`는 실제 SSH 접속값으로 바꿉니다.

```bash
ssh MONITOR_USER@MONITOR_HOST \
  'sudo install -d -o "$(id -un)" -g "$(id -gn)" -m 750 /opt/fleaflea-monitoring'
rsync -a --exclude='.env' --exclude='.env.local' --exclude='secrets/' \
  deploy/monitoring/ MONITOR_USER@MONITOR_HOST:/opt/fleaflea-monitoring/
```

모니터링 EC2에서 환경 파일과 Discord 웹훅 파일을 만듭니다.

```bash
cd /opt/fleaflea-monitoring
cp .env.example .env
chmod 600 .env
install -d -m 700 secrets
```

`.env`에 다음 값을 실제 환경에 맞춰 설정합니다.

```dotenv
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=별도로_생성한_강한_비밀번호
GRAFANA_ROOT_URL=http://localhost:3000
TELEMETRY_BIND_ADDRESS=모니터링_EC2의_사설_IP
GRAFANA_BIND_ADDRESS=127.0.0.1
PROMETHEUS_RETENTION_TIME=15d
PROMETHEUS_RETENTION_SIZE=20GB
```

Discord에서 만든 웹훅 URL을 `secrets/discord-webhook-url`에 **한 줄로**
저장하고 `chmod 600 secrets/discord-webhook-url`로 권한을 제한합니다.
웹훅 URL은 저장소나 명령 실행 기록에 남기지 마세요. 이 파일이 없으면
현재 Compose의 Alertmanager가 시작되지 않습니다.

외부 상태 검사의 실제 공개 도메인이 `api.fleaflea.app`과 다르면
`prometheus/targets/blackbox.yml`의 `/readyz` 주소도 바꿉니다. 그런 다음
앱 EC2의 Nginx가 `/readyz`를 공개 API 도메인으로 전달하는지
[Nginx 설정 안내](../deploy/ec2/README.md#nginx-forwarded-headers)와
`deploy/ec2/nginx/observability.conf.example`을 확인하고 모니터링 스택을
시작합니다.

```bash
docker compose --env-file .env -f compose.yml config --quiet
docker compose --env-file .env -f compose.yml up -d --wait
docker compose --env-file .env -f compose.yml ps
curl -fsS http://127.0.0.1:9090/-/ready
curl -fsS http://127.0.0.1:3100/ready
curl -fsS http://127.0.0.1:3200/ready
curl -fsS http://127.0.0.1:3000/api/health
```

SSM 관리형 인스턴스라면 작업 PC에서 다음처럼 Grafana 포트를 포워딩할 수
있습니다. `MONITOR_INSTANCE_ID`는 모니터링 EC2의 인스턴스 ID로 바꾸고,
작업 PC의 `3000` 포트가 비어 있어야 합니다. 세션을 유지한 채
`http://localhost:3000`으로 접속합니다.

```bash
aws ssm start-session \
  --target MONITOR_INSTANCE_ID \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3000"],"localPortNumber":["3000"]}'
```

## 3. 앱 EC2에 PostgreSQL 모니터링 계정 만들기

기존 PostgreSQL 컨테이너가 실행 중이어야 합니다. DB 관리자 계정으로
`psql`에 접속합니다. 다음 명령은 컨테이너의 `POSTGRES_USER`와
`POSTGRES_DB` 환경 변수를 사용합니다.

```bash
sudo docker exec -it fleaflea-postgres sh -lc \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

`psql`에서 별도 계정을 만들고 필요한 권한을 부여합니다. 이미 계정이
있다면 `CREATE ROLE`은 생략합니다. `\password`는 비밀번호를 대화형으로
설정하므로 SQL 예제나 셸 기록에 비밀번호를 남기지 않습니다.

```sql
CREATE ROLE fleaflea_monitor LOGIN;
GRANT CONNECT ON DATABASE fleaflea_db TO fleaflea_monitor;
GRANT pg_monitor TO fleaflea_monitor;
\password fleaflea_monitor
```

PostgreSQL의 호스트 공개 포트를 추가할 필요는 없습니다. Exporter가
`postgres:5432`라는 Compose 서비스 이름으로 내부 접속합니다.

## 4. 앱 EC2에 Alloy 설정과 비밀 파일 준비

앱 EC2의 `/etc/fleaflea/monitoring.env`는 다음 배포에서 모니터링 프로필을
활성화하는 스위치입니다. 저장소의
[`deploy/ec2/monitoring.env.example`](../deploy/ec2/monitoring.env.example)을
참고해 **앱 EC2에서** 작성하고 권한을 제한합니다.

```bash
sudo install -d -o root -g root -m 700 /etc/fleaflea
if ! sudo test -e /etc/fleaflea/monitoring.env; then
  sudo install -o root -g root -m 600 /dev/null /etc/fleaflea/monitoring.env
fi
sudoedit /etc/fleaflea/monitoring.env
sudo chmod 600 /etc/fleaflea/monitoring.env
```

파일 내용 예시는 다음과 같습니다. `10.0.2.20`은 템플릿의 예시 IP일
뿐입니다. 세 URL에 **2단계에서 사용한 모니터링 EC2의 실제 사설 IP**를
입력합니다. `DATA_SOURCE_PASS`에는 3단계에서 설정한 비밀번호를 넣습니다.

```dotenv
PROMETHEUS_REMOTE_WRITE_URL=http://모니터링_EC2_사설_IP:9090/api/v1/write
LOKI_WRITE_URL=http://모니터링_EC2_사설_IP:3100/loki/api/v1/push
TEMPO_OTLP_ENDPOINT=모니터링_EC2_사설_IP:4317
APP_ENV=production
INSTANCE_NAME=fleaflea-app-1
OTEL_JAVAAGENT_ENABLED=true
OTEL_TRACES_SAMPLER_ARG=0.1
DATA_SOURCE_URI=postgres:5432/fleaflea_db?sslmode=disable
DATA_SOURCE_USER=fleaflea_monitor
DATA_SOURCE_PASS=3단계에서_설정한_비밀번호
```

`INSTANCE_NAME`은 앱 EC2마다 고유하게 지정합니다. 운영 앱의 기본 trace
샘플링 비율은 10%이므로 모든 요청이 Tempo에 나타나지는 않습니다.

GitHub Actions의 CD는 `deploy/ec2/compose.yml`, `deploy/ec2/deploy.sh`,
`deploy/ec2/alloy/config.alloy`를 앱 EC2에 복사합니다. 이후 배포 스크립트가
`/etc/fleaflea/monitoring.env`를 발견하면 `monitoring` 프로필로 Alloy,
Node Exporter, cAdvisor, PostgreSQL Exporter를 함께 시작하고 앱의
OTLP 전송을 켭니다. 비밀 파일 자체는 CD가 만들거나 전송하지 않습니다.
CD는 배포된 Git 리비전의 파일을 사용하므로 로컬에서만 수정한 설정은
EC2에 자동 반영되지 않습니다.
**아직 한 번도 배포하지 않았다면**, 먼저 기존
[EC2 배포 절차](../deploy/ec2/README.md)를 완료하세요.

다음 CD 배포를 기다리지 않고 **이미 배포된 앱 EC2**에서 적용하려면
기존 `/opt/fleaflea/compose.yml`과 `/opt/fleaflea/alloy/config.alloy`가
현재 저장소 버전인지 확인한 뒤 아래 명령을 실행합니다.

```bash
sudo docker compose \
  --env-file /etc/fleaflea/deploy.env \
  --env-file /etc/fleaflea/monitoring.env \
  --profile monitoring \
  -f /opt/fleaflea/compose.yml up -d --wait
```

## 5. 두 서버에서 연결 확인

앱 EC2에서 먼저 모니터링 서버의 사설 주소에 도달하는지 확인합니다.
`MONITOR_PRIVATE_IP`는 실제 사설 IP로 바꿉니다.

```bash
curl -fsS http://MONITOR_PRIVATE_IP:9090/-/ready
curl -fsS http://MONITOR_PRIVATE_IP:3100/ready
```

그다음 앱 EC2에서 프로필의 컨테이너가 올라왔는지 확인합니다.

```bash
sudo docker compose \
  --env-file /etc/fleaflea/deploy.env \
  --env-file /etc/fleaflea/monitoring.env \
  --profile monitoring \
  -f /opt/fleaflea/compose.yml ps
sudo docker logs --tail 100 fleaflea-alloy
sudo docker logs --tail 100 fleaflea-postgres-exporter
curl -fsS http://127.0.0.1:8080/readyz
curl -fsS http://127.0.0.1:8081/actuator/prometheus | head
```

모니터링 EC2의 Prometheus 또는 Grafana Explore에서 다음 쿼리를 각각
확인합니다. `1`이면 해당 신호가 수집되거나 DB 연결에 성공한 것입니다.

```promql
up{job="fleaflea-app",environment="production"}
up{job="fleaflea-node",environment="production"}
up{job="fleaflea-cadvisor",environment="production"}
up{job="fleaflea-postgres",environment="production"}
pg_up{job="fleaflea-postgres",environment="production"}
probe_success{job="blackbox-http",environment="production"}
```

Grafana의 **Fleaflea Overview**에서 **Environment = production**을 선택합니다.
서버 지표는 **Fleaflea Host Details**에서 **Job = fleaflea-node**를,
DB 지표는 **Fleaflea PostgreSQL Details**에서 **Environment = production**,
**Database = fleaflea_db**를 선택합니다. 호스트 상세 화면의
`monitoring-node`는 모니터링 EC2 자신의 지표입니다.

로그는 **Explore → Loki**에서 아래 쿼리로 확인합니다.

```logql
{service="fleaflea",environment="production"} | json
```

트레이스는 **Explore → Tempo → Search**에서 Service Name `fleaflea`를
선택해 확인합니다. `/readyz`는 요청 완료 로그와 Tempo trace에서 제외되므로 로그·트레이스
확인에는 실제 API 요청을 발생시키세요. 인증 없이
`GET https://api.fleaflea.app/api/v1/items/1`을 요청하면 현재 API 설정상
`401`이 나며 요청 완료 로그를 확인할 수 있습니다. 트레이스는 샘플링으로
빠질 수 있습니다.

## 6. 값이 안 보일 때

| 증상 | 먼저 확인할 곳 |
| --- | --- |
| 앱·DB·호스트 지표가 모두 없음 | `fleaflea-alloy` 실행 여부, `monitoring.env` 존재, 앱 EC2 → 모니터링 EC2 `9090` 연결 |
| 앱 지표만 없음 | `app:8081/actuator/prometheus`, 앱 readiness, Alloy의 `fleaflea-app` 수집 상태 |
| `pg_up=0` | 모니터링 계정 비밀번호·`pg_monitor` 권한, `DATA_SOURCE_URI=postgres:5432/...` |
| Loki 로그만 없음 | Docker socket proxy와 Alloy 로그, 앱 컨테이너 로그, 앱 EC2 → 모니터링 EC2 `3100` 연결 |
| Tempo 트레이스만 없음 | `OTEL_JAVAAGENT_ENABLED`, 앱 요청·샘플링, Alloy 로그, `4317` 연결 |
| Grafana 화면만 비어 있음 | 시간 범위, **Environment**, **Job**, **Database** 선택과 Prometheus 원본 쿼리 |

운영 알림의 수신·복구 확인 순서는
[모니터링 서버 안내](../deploy/monitoring/README.md#alert-delivery)와
[장애 대응 런북](../deploy/monitoring/RUNBOOK.md)을 따릅니다. 설정 변경 중
PostgreSQL 볼륨은 삭제하지 마세요. `docker compose down -v`는 사용하지 않습니다.

AWS 설정 참고: [보안 그룹 규칙](https://docs.aws.amazon.com/vpc/latest/userguide/security-group-rules.html),
[SSM 포트 포워딩](https://docs.aws.amazon.com/systems-manager/latest/userguide/getting-started-specify-session-document.html).
