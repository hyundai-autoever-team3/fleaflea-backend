#!/usr/bin/env bash
set -Eeuo pipefail
IMAGE_FILE=/etc/fleaflea/rollback-image
test -s "$IMAGE_FILE" || { echo "No verified rollback image is recorded" >&2; exit 1; }
exec /opt/fleaflea/deploy.sh "$(cat "$IMAGE_FILE")"
