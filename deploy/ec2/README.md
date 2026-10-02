# EC2 container deployment

Nginx remains a host systemd service. Spring Boot and PostgreSQL run with
Docker Compose. GitHub Actions publishes the application image to GitHub
Container Registry (GHCR), then invokes the deployment through AWS Systems
Manager.

## GitHub and AWS prerequisites

1. The workflow uses `GITHUB_TOKEN` with `packages: write`; no AWS ECR
   repository or ECR IAM permission is required.
2. Keep these GitHub repository variables: `AWS_REGION`, `AWS_ROLE_ARN`, and
   `EC2_INSTANCE_ID`. `DEPLOY_BUCKET` and `ECR_REPOSITORY` are not used.
3. Keep the existing GitHub OIDC role permissions required for SSM deployment.
4. Set the EC2 instance metadata response hop limit to `2` so the containerized
   AWS SDK can use the EC2 instance role for S3 access.

The published image name is:

```text
ghcr.io/hyundai-autoever-team3/fleaflea-backend:<commit-sha>
```

## Private GHCR image access

GHCR packages are private by default. Create a GitHub personal access token
(classic) with only `read:packages`, then create this root-only file on EC2:

```bash
sudo install -d -o root -g root -m 700 /etc/fleaflea
sudo nano /etc/fleaflea/ghcr.env
```

```dotenv
GHCR_USERNAME=YOUR_GITHUB_USERNAME
GHCR_TOKEN=YOUR_READ_PACKAGES_TOKEN
```

```bash
sudo chmod 600 /etc/fleaflea/ghcr.env
```

Authorize the token for the GitHub organization if the organization uses SSO.
If the package is deliberately changed to public, EC2 can pull it anonymously
and `/etc/fleaflea/ghcr.env` may be omitted.

## EC2 files

The deployment workflow installs these versioned files on every deployment:

- `/opt/fleaflea/compose.yml`
- `/opt/fleaflea/deploy.sh`
- `/opt/fleaflea/alloy/config.alloy`
- `/etc/fleaflea/deploy.env`

These secret files must already exist on the instance:

- `/etc/fleaflea/fleaflea.env`
- `/etc/fleaflea/postgres.env`
- `/etc/fleaflea/ghcr.env` for a private GHCR image
- `/etc/fleaflea/monitoring.env` to enable Alloy, Node Exporter, cAdvisor, and
  PostgreSQL Exporter; start from `monitoring.env.example`

Create a read-only monitoring role before enabling the PostgreSQL Exporter.
Connect as the database administrator, replace the example password, and run:

```sql
CREATE USER fleaflea_monitor WITH PASSWORD 'replace-with-a-long-random-password';
GRANT CONNECT ON DATABASE fleaflea_db TO fleaflea_monitor;
GRANT pg_monitor TO fleaflea_monitor;
```

Put the same credentials in `/etc/fleaflea/monitoring.env` as
`DATA_SOURCE_USER` and `DATA_SOURCE_PASS`. The exporter uses
`DATA_SOURCE_URI=postgres:5432/fleaflea_db?sslmode=disable`; it does not reuse
the application or PostgreSQL administrator account.

The PostgreSQL named volume is `fleaflea_postgres_data`. The first container
deployment adopts that existing volume and replaces the legacy application
systemd process with the `fleaflea-app` container.

## Verification

```bash
sudo docker compose --env-file /etc/fleaflea/deploy.env \
  -f /opt/fleaflea/compose.yml ps

curl -fsS http://127.0.0.1:8080/readyz
curl -fsS http://127.0.0.1:8081/actuator/health
curl -fsS http://127.0.0.1:8081/actuator/prometheus | head
```

The application API and readiness endpoint use port 8080. Actuator health and
Prometheus use port 8081, bound to host loopback only. Nginx must proxy
`/readyz`, but it must not proxy `/actuator/**`.

When `/etc/fleaflea/monitoring.env` exists, use both env files and the profile
for manual Compose commands:

```bash
sudo docker compose \
  --env-file /etc/fleaflea/deploy.env \
  --env-file /etc/fleaflea/monitoring.env \
  --profile monitoring \
  -f /opt/fleaflea/compose.yml ps
```

Alloy listens for application OTLP traffic only on the Compose network. Its UI
and self-metrics are bound to `127.0.0.1:12345`. A restricted Docker socket
proxy gives Alloy read access to container discovery and logs without mounting
the socket into Alloy itself. Keep the proxy on its internal Docker network.
cAdvisor requires privileged host access to read container cgroups and Docker
state; it has no published host port and remains on the internal monitoring
network. Review this privilege when the host runtime changes.

## Nginx forwarded headers

The HTTPS server block must pass the original request scheme and host to the
Spring Boot container. Add these headers to the `location` block that proxies
API requests:

```nginx
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host $host;
proxy_set_header X-Forwarded-Port $server_port;
```

Validate and reload Nginx after editing its configuration:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

Use `nginx/observability.conf.example` as the JSON access log and readiness
route template. It records the route without a query string and never exposes
the management port. Install `nginx/fleaflea-logrotate.example` under
`/etc/logrotate.d/` after reviewing the host paths.

Verify the OpenAPI server URL and an HTTPS preflight response:

```bash
curl -fsS https://api.fleaflea.app/v3/api-docs \
  | grep -o '"servers":\[[^]]*\]'

curl -i -X OPTIONS \
  'https://api.fleaflea.app/api/v1/friend-requests/1/reject' \
  -H 'Origin: https://fleaflea.app' \
  -H 'Access-Control-Request-Method: POST'
```

Verify the public readiness route and confirm the management route is not
public:

```bash
curl -fsS https://api.fleaflea.app/readyz
test "$(curl -sS -o /dev/null -w '%{http_code}' \
  https://api.fleaflea.app/actuator/prometheus)" = "404"
```

Do not run `docker compose down -v`; it removes the PostgreSQL data volume.
