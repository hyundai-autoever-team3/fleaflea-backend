#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ $EUID -ne 0 || $# -ne 2 || ! "$1" =~ @sha256:[0-9a-f]{64}$ || ! "$2" =~ ^[0-9a-f]{40}$ ]]; then
    echo "usage: sudo $0 <immutable-dev-image-uri> <source-commit-sha>" >&2
    exit 2
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
METADATA=http://metadata.google.internal/computeMetadata/v1
INSTANCE="$(curl -fsS --max-time 5 -H 'Metadata-Flavor: Google' "$METADATA/instance/name")"
PROJECT="$(curl -fsS --max-time 5 -H 'Metadata-Flavor: Google' "$METADATA/project/project-id")"
INSTANCE_ID="$(curl -fsS --max-time 5 -H 'Metadata-Flavor: Google' "$METADATA/instance/id")"
python3 "$SCRIPT_DIR/validate.py" --check-target "$1" "$INSTANCE" "$PROJECT" "$INSTANCE_ID"

exec 9>/run/lock/fleaflea-dev-deploy.lock
flock -n 9 || { echo "Another dev deployment is running" >&2; exit 2; }

DEV_ENV_FILE=/etc/fleaflea-dev/runtime.env
export DEV_ENV_FILE APP_IMAGE APP_VERSION
APP_IMAGE="$1"
APP_VERSION="$2"
python3 "$SCRIPT_DIR/validate.py" "$DEV_ENV_FILE"

compose() {
    docker compose --project-name fleaflea-dev --env-file "$DEV_ENV_FILE" \
        -f "$SCRIPT_DIR/compose.yml" "$@"
}

PREVIOUS_IMAGE=""
PREVIOUS_VERSION=""
if [[ -f /etc/fleaflea-dev/current-image ]]; then
    PREVIOUS_IMAGE="$(cat /etc/fleaflea-dev/current-image)"
    python3 "$SCRIPT_DIR/validate.py" --check-target "$PREVIOUS_IMAGE" "$INSTANCE" "$PROJECT" "$INSTANCE_ID"
    PREVIOUS_VERSION="$(cat /etc/fleaflea-dev/current-version)"
    if [[ ! "$PREVIOUS_VERSION" =~ ^[0-9a-f]{40}$ ]]; then
        echo "Invalid previous dev source revision" >&2
        exit 2
    fi
fi

export DOCKER_CONFIG
DOCKER_CONFIG="$(mktemp -d)"
trap 'rm -rf "$DOCKER_CONFIG"' EXIT
curl -fsS --max-time 10 -H 'Metadata-Flavor: Google' \
    "$METADATA/instance/service-accounts/default/token" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])' \
    | docker login "${APP_IMAGE%%/*}" --username oauth2accesstoken --password-stdin

compose pull app
if ! compose up -d --wait --wait-timeout 240 || \
        ! curl -fsS --max-time 5 http://127.0.0.1:18080/readyz >/dev/null; then
    if [[ -n "$PREVIOUS_IMAGE" ]]; then
        export APP_IMAGE="$PREVIOUS_IMAGE"
        export APP_VERSION="$PREVIOUS_VERSION"
        compose up -d --wait --wait-timeout 240 || echo "Previous dev image also failed health checks" >&2
    else
        compose stop app
    fi
    echo "Dev deployment failed; inspect dev logs and migration compatibility" >&2
    exit 1
fi

if [[ -n "$PREVIOUS_IMAGE" ]]; then
    printf '%s\n' "$PREVIOUS_IMAGE" > /etc/fleaflea-dev/rollback-image
    printf '%s\n' "$PREVIOUS_VERSION" > /etc/fleaflea-dev/rollback-version
fi
printf '%s\n' "$APP_VERSION" > /etc/fleaflea-dev/current-version
printf '%s\n' "$APP_IMAGE" > /etc/fleaflea-dev/current-image.tmp
mv /etc/fleaflea-dev/current-image.tmp /etc/fleaflea-dev/current-image
echo "Dev deployment is healthy: $APP_IMAGE"
