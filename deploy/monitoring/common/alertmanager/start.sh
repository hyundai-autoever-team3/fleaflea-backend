#!/bin/sh
set -eu

# Reject template syntax and characters that would break the Markdown link.
: "${DASHBOARD_URL:?DASHBOARD_URL is required}"
if ! printf '%s\n' "$DASHBOARD_URL" | grep -Eq '^https?://[a-zA-Z0-9._~:/?#@!$&+,;=%-]+$'; then
  echo 'DASHBOARD_URL must be an HTTP(S) URL without spaces or template syntax' >&2
  exit 1
fi

umask 077
printf '{{ define "dashboard.url" }}%s{{ end }}\n' "$DASHBOARD_URL" > /run/alertmanager/dashboard.tmpl
exec /bin/alertmanager "$@"
