# 로컬 앱 수집기

`compose.yml`은 상위 모니터링 Compose에 함께 적용하는 로컬 수집 설정입니다.
`config.alloy`는 Spring·PostgreSQL 지표, 앱 파일 로그와 OTLP 트레이스를 수집합니다.
`blackbox/`는 로컬 앱과 GCP 공개 URL의 접속 확인 설정·검사 대상을 보관합니다.
`prometheus/`는 Blackbox 검사를 포함하는 로컬 Prometheus 설정입니다.

## 실행

저장소 루트에서 공통 Compose를 먼저 지정합니다. 상대 경로는 공통 Compose 위치를 기준으로 합니다.
상위 안내에 따라 `.env.local`과 Discord 웹훅 파일을 먼저 준비합니다.

```bash
mkdir -p build/logs
docker compose --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/local/compose.yml up -d
```

로컬 앱은 API `8080`·Actuator `8081`, PostgreSQL은 `5432`를 사용합니다.
앱 실행 환경·OpenTelemetry 에이전트 설정은 [상위 README](../README.md)를 참고합니다.
이 Compose가 로컬 Spring 앱이나 PostgreSQL 서버를 실행하지는 않습니다.

## 중지

```bash
docker compose --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/local/compose.yml stop
```

공통 저장 볼륨과 수집기의 데이터 볼륨은 유지됩니다.
