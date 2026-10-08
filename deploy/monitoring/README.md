# Fleaflea monitoring server

This stack stores Fleaflea metrics, logs, and traces on a host separate from the
application EC2 instance. It runs Prometheus, Loki, Tempo, Grafana,
Alertmanager, Blackbox Exporter, and Node Exporter.

For the complete app EC2 and monitoring EC2 connection procedure, see the
[EC2 monitoring connection guide](../../docs/EC2_MONITORING_CONNECTION.md).

## Local development

한국어로 된 전체 설정·실행·조회·문제 해결 절차는
[`docs/LOCAL_MONITORING.md`](../../docs/LOCAL_MONITORING.md)를 참고합니다.
지표·로그·트레이스의 해석과 조회 쿼리는
[`docs/MONITORING_VIEWING_GUIDE.md`](../../docs/MONITORING_VIEWING_GUIDE.md)에 있습니다.

Run these commands from the repository root.

1. Ensure the local PostgreSQL server is running on port `5432` and contains
   `fleaflea_db`. Use the same database credentials in `.env` and
   `deploy/monitoring/.env.local` (`LOCAL_DB_USERNAME` and `LOCAL_DB_PASSWORD`).

   ```bash
   psql -h 127.0.0.1 -p 5432 -U postgres -d fleaflea_db -c 'SELECT 1'
   ```

   The PostgreSQL container in `performance/compose.yml` uses host port `5435`
   for isolated performance tests; it is not used by this local monitoring setup.

2. Create the local monitoring environment file. Change the Grafana password
   and use an HTTP root URL locally:

   ```bash
   cp deploy/monitoring/.env.example deploy/monitoring/.env.local
   ```

   ```dotenv
   GRAFANA_ADMIN_PASSWORD=local-grafana-password
   GRAFANA_ROOT_URL=http://localhost:3000
   ```

   Save the Discord webhook URL as the only line in
   `deploy/monitoring/secrets/discord-webhook-url`, then set its permissions to
   `600`. This Git-ignored file is required before starting Alertmanager.

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

5. Start the application:

   ```bash
   ./gradlew bootRun -PotelAgent
   ```

   For an IntelliJ Application run, first run `./gradlew prepareOtelAgent`,
   then add `-javaagent:<repository>/build/otel/opentelemetry-javaagent.jar`
   to VM options and set the same `OTEL_*` environment variables in the run
   configuration. Keep `LOG_FORMAT_CONSOLE` unset for readable terminal logs.

Open Grafana at `http://localhost:3000` and select **Fleaflea Overview**.
For server metrics, open **Fleaflea Host Details** (adapted from [Node Exporter Full,
ID 1860](https://grafana.com/grafana/dashboards/1860-node-exporter-full/)).
For database metrics, open **Fleaflea PostgreSQL Details**. The latter uses the
metrics actually collected by this stack; the community PostgreSQL dashboard
ID 9628 expects Kubernetes labels that this deployment does not provide.
The local Node Exporter reports Docker Desktop's Linux VM, not the macOS host.
Dashboard titles, panel names, and legends use English; Fleaflea-authored panel
descriptions use Korean. Spring Boot Statistics dashboard ID 19004 expects
`application` and `namespace` labels, while this stack uses `service`,
`environment`, and `instance`, so it is not imported unchanged.
The three dashboards are provisioned from `grafana/dashboards/`; Grafana checks
the files every 30 seconds, and UI edits are disabled. Change the JSON files
when updating panels.
Prometheus is available at `http://localhost:9090`, Loki at port `3100`, Tempo
at port `3200`, and the local Alloy UI at `http://localhost:12346`.

The local Alloy override scrapes application metrics from port `8081`, receives
OTLP/HTTP traces on port `4318`, tails `build/logs/*.json`, and scrapes the
PostgreSQL exporter connected to the local database on port `5432`.
The database must accept connections from Docker via `host.docker.internal`.

Stop the local stack without deleting its named volumes:

```bash
docker compose \
  --env-file deploy/monitoring/.env.local \
  -f deploy/monitoring/compose.yml \
  -f deploy/monitoring/compose.local.yml \
  down
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

The `discord` Alertmanager receiver reads its webhook URL from a Docker Compose
secret. Before starting this stack, save the Discord webhook URL as the only
line in `secrets/discord-webhook-url` (relative to this directory), then run
`chmod 600 secrets/discord-webhook-url`. The `secrets/` directory is ignored by
Git. Alertmanager cannot start without this file. On the monitoring server,
provision the same file through your secret manager or deployment process.
Restart Alertmanager, trigger a temporary alert, and verify both firing and
resolved notifications in Discord.

Grafana provisions Alertmanager as a data source, so firing Prometheus alerts
are visible in Grafana independently of Discord delivery. The webhook secret
must still exist for Alertmanager to start.

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
`trace_id` link to Tempo, and use Tempo's logs link to return to Loki.

Volumes preserve data across `docker compose down` and restarts. Never run
`docker compose down -v` during routine operation. Confirm retention deletion
and disk alerts in a staging environment before relying on the configured
limits.
