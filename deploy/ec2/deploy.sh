#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <image-uri>" >&2
  exit 2
fi

IMAGE_URI="$1"
COMPOSE_FILE="/opt/fleaflea/compose.yml"
DEPLOY_ENV="/etc/fleaflea/deploy.env"
GHCR_ENV="/etc/fleaflea/ghcr.env"

legacy_service_was_active=false
if systemctl is-active --quiet fleaflea 2>/dev/null; then
  legacy_service_was_active=true
  systemctl stop fleaflea
fi

if docker inspect fleaflea-postgres >/dev/null 2>&1; then
  current_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' fleaflea-postgres 2>/dev/null || true)"
  if [[ "$current_project" != "fleaflea" ]]; then
    docker rm -f fleaflea-postgres
  fi
fi

if [[ -f "$GHCR_ENV" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$GHCR_ENV"
  set +a
  : "${GHCR_USERNAME:?GHCR_USERNAME is required in $GHCR_ENV}"
  : "${GHCR_TOKEN:?GHCR_TOKEN is required in $GHCR_ENV}"
  printf '%s' "$GHCR_TOKEN" \
    | docker login ghcr.io --username "$GHCR_USERNAME" --password-stdin
fi

previous_image="$(docker inspect --format '{{.Config.Image}}' fleaflea-app 2>/dev/null || true)"

write_image_env() {
  local image_uri="$1"
  local temporary_file
  temporary_file="$(mktemp)"
  printf 'APP_IMAGE=%s\n' "$image_uri" > "$temporary_file"
  install -o root -g root -m 600 "$temporary_file" "$DEPLOY_ENV"
  rm -f "$temporary_file"
}

start_stack() {
  docker compose --env-file "$DEPLOY_ENV" -f "$COMPOSE_FILE" pull app
  docker compose --env-file "$DEPLOY_ENV" -f "$COMPOSE_FILE" up -d --wait --wait-timeout 180
  curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null
}

write_image_env "$IMAGE_URI"

if start_stack; then
  systemctl disable fleaflea 2>/dev/null || true
  docker image prune -f >/dev/null
  echo "Deployment completed: $IMAGE_URI"
  exit 0
fi

echo "Deployment failed; attempting rollback" >&2

if [[ -n "$previous_image" ]]; then
  write_image_env "$previous_image"
  start_stack
  echo "Rollback completed: $previous_image" >&2
else
  echo "No previous application image is available" >&2
  if [[ "$legacy_service_was_active" == "true" ]]; then
    systemctl start fleaflea
    echo "Legacy systemd application was restarted" >&2
  fi
fi

exit 1
