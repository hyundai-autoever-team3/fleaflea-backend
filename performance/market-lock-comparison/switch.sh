#!/usr/bin/env bash
set -euo pipefail
cd /opt/fleaflea-perf
export VARIANT="${1:?variant required}"
[[ "$VARIANT" == plain || "$VARIANT" == db || "$VARIANT" == redisson-db ]] || exit 2
compose=(sudo --preserve-env=VARIANT docker compose -f compose.yml -f lock186/compose.override.yml)
"${compose[@]}" stop app app-replica >/dev/null
if [[ -f lock186/seed.dump ]]; then
  "${compose[@]}" exec -T postgres dropdb -U postgres --if-exists fleaflea_db
  "${compose[@]}" exec -T postgres createdb -U postgres -T template0 fleaflea_db
  "${compose[@]}" exec -T postgres pg_restore -U postgres -d fleaflea_db < lock186/seed.dump
fi
"${compose[@]}" up -d --no-deps app app-replica
for node in 8080 8082; do
  ready=false
  for attempt in $(seq 1 90); do
    if curl -fsS "http://127.0.0.1:$node/readyz" >/dev/null 2>&1; then ready=true; break; fi
    sleep 2
  done
  "$ready" || { echo "Node $node failed readiness"; exit 1; }
done
echo "READY $VARIANT"
