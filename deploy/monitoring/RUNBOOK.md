# Fleaflea observability runbook

Start with the alert's time range and deployment version. Check the external
probe, application request metrics, JSON logs, and a sampled trace in that
order. A trace can be absent because production sampling is intentional; logs
and metrics remain the source of truth for unsampled failures.

## external-down

1. Check `probe_http_status_code` and TLS probe metrics in Prometheus.
2. From outside the application EC2, request `https://api.fleaflea.app/readyz`.
3. Check Nginx, then `curl http://127.0.0.1:8080/readyz` on the EC2 host.
4. Check the app and PostgreSQL container health. Restore service, then wait for
   the alert's resolved notification.

## high-5xx

1. Break down `http_server_requests_seconds_count` by `uri` and status.
2. Search Loki for the same period with `{service="fleaflea"} | json | status >= 500`.
3. Use `errorCode`, deployment version, and trace ID to identify the failing
   boundary. Roll back only when the failure aligns with the current deploy.

## slow-api

1. Identify the route from the p95 alert and compare request volume.
2. Check JVM pause, Hikari pending connections, PostgreSQL locks, and host CPU.
3. Open a sampled slow trace and compare image validation, S3, and database
   spans. Do not infer a database lock from duration alone.

## database-pool

1. Compare Hikari active, idle, pending, and timeout metrics.
2. Check PostgreSQL connections, locks, deadlocks, and long-running queries.
3. Mitigate the slow or blocked work before increasing the pool size.

## sse-rejected

1. Group `fleaflea_sse_tasks_total` by task type and outcome.
2. Check `executor_queued_tasks` and `executor_active_threads` for
   `notificationSseExecutor`.
3. Check SSE send failures and connection count. A send success means the
   server wrote to the connection; it does not prove the client displayed it.

## telemetry-missing

1. Check the `fleaflea-alloy` container and its `/metrics` endpoint on port
   12345.
2. Verify scrape targets inside Alloy, its WAL/queue disk, and connectivity to
   ports 9090, 3100, and 4317 on the monitoring server.
3. Telemetry failure must not block application traffic. Restore collection and
   confirm that new data arrives; gaps cannot always be backfilled.

## disk-space

1. Identify the filesystem and largest volume before deleting anything.
2. Check Prometheus, Loki, Tempo, Docker, and Nginx retention or rotation.
3. Preserve PostgreSQL data. Never remove `fleaflea_postgres_data` as a cleanup
   action.

## monitoring-target-down

1. Check container health and recent logs for the named target.
2. Check disk space and configuration validity.
3. Restore the component and verify ingestion as well as its readiness URL.
