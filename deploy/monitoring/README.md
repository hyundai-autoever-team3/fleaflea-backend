# Fleaflea monitoring server

This stack stores Fleaflea metrics, logs, and traces on a host separate from the
application EC2 instance. It runs Prometheus, Loki, Tempo, Grafana,
Alertmanager, Blackbox Exporter, and Node Exporter.

## Local development

한국어로 된 전체 설정·실행·조회·문제 해결 절차는
[`docs/LOCAL_MONITORING.md`](../../docs/LOCAL_MONITORING.md)를 참고합니다.
지표·로그·트레이스의 해석과 조회 쿼리는
[`docs/MONITORING_VIEWING_GUIDE.md`](../../docs/MONITORING_VIEWING_GUIDE.md)에 있습니다.

Run these commands from the repository root.

1. Start the local PostgreSQL container:

   ```bash
   docker compose -f performance/compose.yml up -d postgres
   ```

2. Create the local monitoring environment file. Change the Grafana password
   and use an HTTP root URL locally:

   ```bash
   cp deploy/monitoring/.env.example deploy/monitoring/.env.local
   ```

   ```dotenv
   GRAFANA_ADMIN_PASSWORD=local-grafana-password
   GRAFANA_ROOT_URL=http://localhost:3000
   ```

3. Prepare the application log directory, then start the monitoring stack and
   local Alloy override:

   ```bash
   mkdir -p build/logs
   docker compose \
     --env-file deploy/monitoring/.env.local \
     -f deploy/monitoring/compose.yml \
     -f deploy/monitoring/compose.local.yml \
     up -d --wait
   ```

4. Copy `.env.example` to `.env` if it does not exist and enable these local
   observability values:

   ```dotenv
   LOG_FORMAT_CONSOLE=ecs
   APP_ENV=local
   APP_VERSION=local
   MANAGEMENT_PORT=8081
   TRACE_SAMPLING_PROBABILITY=1.0
   OTLP_TRACES_ENABLED=true
   OTLP_TRACES_ENDPOINT=http://localhost:4318/v1/traces
   LOGGING_FILE_NAME=build/logs/fleaflea.json
   LOGGING_STRUCTURED_FORMAT_FILE=ecs
   ```

5. Start the application:

   ```bash
   ./gradlew bootRun
   ```

Open Grafana at `http://localhost:3000` and select **Fleaflea Overview**.
Prometheus is available at `http://localhost:9090`, Loki at port `3100`, Tempo
at port `3200`, and the local Alloy UI at `http://localhost:12346`.

The local Alloy override scrapes application metrics from port `8081`, receives
OTLP/HTTP traces on port `4318`, tails `build/logs/*.json`, and scrapes the
PostgreSQL exporter connected to port `5435`.

Stop the local stack without deleting its named volumes:

```bash
docker compose \
  --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/compose.local.yml \
  down
docker compose -f performance/compose.yml stop postgres
```

## Network policy

Use the monitoring server's private address for telemetry ingestion. Permit the
following ports only from the application EC2 security group:

- `9090/tcp`: Prometheus remote write
- `3100/tcp`: Loki push API
- `4317/tcp`: Tempo OTLP gRPC

Keep Grafana on `127.0.0.1:3000` and reach it through an authenticated reverse
proxy, VPN, or SSM port forwarding. Do not expose Prometheus, Loki, Tempo, or
Alertmanager to the public internet. The repository configuration has no
application-level authentication on their ingestion endpoints, so the network
restriction is required.

## Start

```bash
cd /opt/fleaflea-monitoring
cp .env.example .env
chmod 600 .env
# Edit the password, URLs, bind addresses, retention time, and size.
docker compose config --quiet
docker compose up -d --wait
```

The production `.env` normally uses the monitoring server's private IP for
`TELEMETRY_BIND_ADDRESS`. Restrict that IP and the three ingestion ports in the
security group before starting the stack.

Then copy `deploy/ec2/monitoring.env.example` to
`/etc/fleaflea/monitoring.env` on the application EC2, replace the example
private address, and redeploy. Its presence enables the Compose `monitoring`
profile. Removing the file and redeploying disables Alloy and the exporters;
the application continues with trace export disabled.

## External readiness target

Edit `prometheus/targets/blackbox.yml` if the public hostname changes. The
probe checks `/readyz`, which includes application readiness and PostgreSQL.
The management port's health endpoint is not used to declare the public service
healthy.

## Alert delivery

Alert rules are active immediately, but the checked-in Alertmanager receiver is
deliberately empty because the team destination and secret are not repository
data. Before production, replace `unconfigured` in
`alertmanager/alertmanager.yml` with the team's Slack, email, PagerDuty, or
webhook receiver. Store its secret in a root-only file or secret manager; do not
commit it. Restart Alertmanager, trigger a temporary always-firing alert, and
verify both firing and resolved notifications.

Grafana provisions Alertmanager as a data source, so firing Prometheus alerts
are visible in Grafana even before an external receiver is connected.

## Verification

```bash
docker compose ps
curl -fsS http://127.0.0.1:9090/-/ready
curl -fsS http://127.0.0.1:3100/ready
curl -fsS http://127.0.0.1:3200/ready
curl -fsS http://127.0.0.1:3000/api/health
```

In Prometheus, verify `up{job="fleaflea-app"} == 1`. In Grafana, open
**Fleaflea / Fleaflea Overview**, find an application JSON log, follow its
`traceId` link to Tempo, and use Tempo's logs link to return to Loki.

Volumes preserve data across `docker compose down` and restarts. Never run
`docker compose down -v` during routine operation. Confirm retention deletion
and disk alerts in a staging environment before relying on the configured
limits.
