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
OBSERVE_SECONDS="${OBSERVE_SECONDS:-60}"
DRAIN_SECONDS="${DRAIN_SECONDS:-90}"
if [[ ! "$OBSERVE_SECONDS" =~ ^[0-9]+$ || "$OBSERVE_SECONDS" -gt 300 ||
      ! "$DRAIN_SECONDS" =~ ^[0-9]+$ || "$DRAIN_SECONDS" -gt 300 ]]; then
    echo "Invalid observation or drain interval" >&2
    exit 2
fi
COMPOSE_FILE="/opt/fleaflea/compose.app.yml"
STATE_FILE="/etc/fleaflea/active-slot"
UPSTREAM_FILE="/etc/nginx/conf.d/fleaflea-backend.conf"
PREVIOUS_SLOT=""
PREVIOUS_IMAGE=""
if [[ -f "$STATE_FILE" ]]; then
    PREVIOUS_SLOT="$(cat "$STATE_FILE")"
    if [[ "$PREVIOUS_SLOT" != blue && "$PREVIOUS_SLOT" != green ]]; then
        echo "Invalid active deployment slot" >&2
        exit 2
    fi
    PREVIOUS_IMAGE="$(sed -n 's/^APP_IMAGE=//p' "/etc/fleaflea/$PREVIOUS_SLOT.env")"
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

ACCESS_LOG=/var/log/nginx/fleaflea-access.log
ACCESS_OFFSET=0
if [[ -f "$ACCESS_LOG" ]]; then
    ACCESS_OFFSET="$(stat -c %s "$ACCESS_LOG")"
fi
printf 'upstream fleaflea_backend { server 127.0.0.1:%s; keepalive 32; }\n' \
    "$TARGET_PORT" > "$UPSTREAM_FILE"
if ! nginx -t || ! systemctl reload nginx; then
    restore_upstream
    compose_target stop app
    rm -f "$BACKUP_FILE"
    echo "Nginx switch failed; previous upstream restored" >&2
    exit 1
fi

if ! curl --fail --silent --show-error --max-time 10 \
        http://127.0.0.1/actuator/health >/dev/null; then
    restore_upstream
    compose_target stop app
    rm -f "$BACKUP_FILE"
    echo "Proxy verification failed; previous upstream restored" >&2
    exit 1
fi

# Keep the previous process available while checking the switched proxy repeatedly.
OBSERVE_UNTIL=$((SECONDS + OBSERVE_SECONDS))
while (( SECONDS < OBSERVE_UNTIL )); do
    if ! curl --fail --silent --show-error --max-time 10 \
        http://127.0.0.1/actuator/health >/dev/null; then
        restore_upstream
        compose_target stop app
        rm -f "$BACKUP_FILE"
        echo "Post-switch observation failed; previous upstream restored" >&2
        exit 1
    fi
    sleep 5
done

if [[ -f "$ACCESS_LOG" ]] && ! python3 - "$ACCESS_LOG" "$ACCESS_OFFSET" "$TARGET_PORT" <<'PY'
import json
import os
import sys

path, offset, port = sys.argv[1:]
total = failures = 0
with open(path) as access_log:
    offset = int(offset)
    access_log.seek(offset if os.path.getsize(path) >= offset else 0)
    for line in access_log:
        try:
            request = json.loads(line)
        except ValueError:
            continue
        if request.get('upstream') != '127.0.0.1:' + port or not request.get('uri', '').startswith('/api/'):
            continue
        total += 1
        failures += int(request.get('status', 0) >= 500)
print(f'Post-switch API observations: requests={total}, 5xx={failures}')
sys.exit(1 if total >= 20 and failures / total > 0.05 else 0)
PY
then
    restore_upstream
    compose_target stop app
    rm -f "$BACKUP_FILE"
    echo "New upstream API error ratio exceeded 5%; previous upstream restored" >&2
    exit 1
fi

if [[ -n "$PREVIOUS_IMAGE" ]]; then
    printf '%s\n' "$PREVIOUS_IMAGE" > /etc/fleaflea/rollback-image.tmp
    mv /etc/fleaflea/rollback-image.tmp /etc/fleaflea/rollback-image
fi

printf '%s\n' "$TARGET_SLOT" > "$STATE_FILE.tmp"
mv "$STATE_FILE.tmp" "$STATE_FILE"
rm -f "$BACKUP_FILE"
echo "Deployment switched to $TARGET_SLOT: $IMAGE_URI"

if [[ -n "$PREVIOUS_SLOT" ]]; then
    # Keep existing HTTP/WebSocket connections on the old process during drain.
    sleep "$DRAIN_SECONDS"
    docker compose --project-name "fleaflea-$PREVIOUS_SLOT" \
        --env-file "/etc/fleaflea/$PREVIOUS_SLOT.env" -f "$COMPOSE_FILE" stop app
fi
