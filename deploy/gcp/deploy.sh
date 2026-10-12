#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ $# -ne 1 || ! "$1" =~ ^[a-zA-Z0-9][a-zA-Z0-9./:@_-]+$ ]]; then
    echo "usage: $0 <immutable-image-uri>" >&2
    exit 2
fi

exec 9>/run/lock/fleaflea-deploy.lock
flock -n 9 || { echo "Another deployment is running" >&2; exit 2; }

IMAGE_URI="$1"
COMPOSE_FILE="/opt/fleaflea/compose.app.yml"
STATE_FILE="/etc/fleaflea/active-slot"
UPSTREAM_FILE="/etc/nginx/conf.d/fleaflea-backend.conf"
PREVIOUS_SLOT=""
if [[ -f "$STATE_FILE" ]]; then
    PREVIOUS_SLOT="$(cat "$STATE_FILE")"
    if [[ "$PREVIOUS_SLOT" != blue && "$PREVIOUS_SLOT" != green ]]; then
        echo "Invalid active deployment slot" >&2
        exit 2
    fi
fi

TARGET_SLOT=blue
TARGET_PORT=8081
if [[ "$PREVIOUS_SLOT" == blue ]]; then
    TARGET_SLOT=green
    TARGET_PORT=8082
fi

SLOT_ENV="/etc/fleaflea/$TARGET_SLOT.env"
printf 'APP_IMAGE=%s\nAPP_PORT=%s\n' "$IMAGE_URI" "$TARGET_PORT" > "$SLOT_ENV"
compose_target() {
    docker compose --project-name "fleaflea-$TARGET_SLOT" \
        --env-file "$SLOT_ENV" -f "$COMPOSE_FILE" "$@"
}

compose_target config --quiet
REGISTRY_HOST="${IMAGE_URI%%/*}"
if [[ "$REGISTRY_HOST" == *.pkg.dev ]]; then
    export DOCKER_CONFIG
    DOCKER_CONFIG="$(mktemp -d)"
    trap 'rm -rf "$DOCKER_CONFIG"' EXIT
    curl --fail --silent --show-error --max-time 10 \
        -H 'Metadata-Flavor: Google' \
        http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token \
        | python3 -c 'import json, sys; print(json.load(sys.stdin)["access_token"])' \
        | docker login "$REGISTRY_HOST" --username oauth2accesstoken --password-stdin
fi
compose_target pull app
if ! compose_target up -d --wait --wait-timeout 240; then
    compose_target stop app
    echo "New application failed health checks; current upstream preserved" >&2
    exit 1
fi

if ! curl --fail --silent --show-error --max-time 10 \
        "http://127.0.0.1:$TARGET_PORT/actuator/health" >/dev/null; then
    compose_target stop app
    exit 1
fi

BACKUP_FILE="$(mktemp)"
HAD_UPSTREAM=false
if [[ -f "$UPSTREAM_FILE" ]]; then
    cp "$UPSTREAM_FILE" "$BACKUP_FILE"
    HAD_UPSTREAM=true
fi

restore_upstream() {
    if [[ "$HAD_UPSTREAM" == true ]]; then
        cp "$BACKUP_FILE" "$UPSTREAM_FILE"
    else
        rm -f "$UPSTREAM_FILE"
    fi
    nginx -t && systemctl reload nginx
}

printf 'upstream fleaflea_backend { server 127.0.0.1:%s; keepalive 32; }\n' \
    "$TARGET_PORT" > "$UPSTREAM_FILE"
if ! nginx -t || ! systemctl reload nginx; then
    restore_upstream
    rm -f "$BACKUP_FILE"
    echo "Nginx switch failed; previous upstream restored" >&2
    exit 1
fi

if ! curl --fail --silent --show-error --max-time 10 \
        http://127.0.0.1/actuator/health >/dev/null; then
    restore_upstream
    rm -f "$BACKUP_FILE"
    echo "Proxy verification failed; previous upstream restored" >&2
    exit 1
fi

printf '%s\n' "$TARGET_SLOT" > "$STATE_FILE.tmp"
mv "$STATE_FILE.tmp" "$STATE_FILE"
rm -f "$BACKUP_FILE"
echo "Deployment switched to $TARGET_SLOT: $IMAGE_URI"

if [[ -n "$PREVIOUS_SLOT" ]]; then
    # Keep existing HTTP/WebSocket connections on the old process during drain.
    sleep "${DRAIN_SECONDS:-90}"
    docker compose --project-name "fleaflea-$PREVIOUS_SLOT" \
        --env-file "/etc/fleaflea/$PREVIOUS_SLOT.env" -f "$COMPOSE_FILE" stop app
fi
