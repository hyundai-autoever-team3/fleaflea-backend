#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ $EUID -ne 0 || $# -ne 1 || ! "$1" =~ ^[a-z0-9][a-z0-9._-]+$ ]]; then
    echo "usage: sudo $0 <backup-bucket>" >&2
    exit 2
fi
SOURCE_DIRECTORY="$(cd "$(dirname "$0")" && pwd)"
install -d -m 700 /var/lib/fleaflea-operations /var/log/fleaflea
install -m 700 "$SOURCE_DIRECTORY/operations.py" /opt/fleaflea/operations.py
python3 -m venv /opt/fleaflea/operations-venv
/opt/fleaflea/operations-venv/bin/pip install --disable-pip-version-check -r "$SOURCE_DIRECTORY/requirements-operations.txt"
printf 'BACKUP_BUCKET=%s\n' "$1" > /etc/fleaflea/operations.env
chmod 600 /etc/fleaflea/operations.env

for OPERATION in backup restore cleanup check; do
    ARGUMENT="$OPERATION"
    EXTRA=""
    if [[ "$OPERATION" == restore ]]; then
        ARGUMENT=backup
        EXTRA=--verify-restore
    fi
    cat > "/etc/systemd/system/fleaflea-$OPERATION.service" <<EOF
[Unit]
Description=FleaFlea $OPERATION
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
EnvironmentFile=/etc/fleaflea/operations.env
ExecStart=/opt/fleaflea/operations-venv/bin/python /opt/fleaflea/operations.py $ARGUMENT $EXTRA
UMask=0077
TimeoutStartSec=1800
StandardOutput=append:/var/log/fleaflea/operations.log
StandardError=append:/var/log/fleaflea/operations.log
EOF
done

write_timer() {
    local OPERATION="$1" SCHEDULE="$2"
    cat > "/etc/systemd/system/fleaflea-$OPERATION.timer" <<EOF
[Unit]
Description=Schedule FleaFlea $OPERATION
[Timer]
OnCalendar=$SCHEDULE
Persistent=true
RandomizedDelaySec=60
[Install]
WantedBy=timers.target
EOF
}
write_timer backup '*-*-* 03:00:00 Asia/Seoul'
write_timer restore 'Sun *-*-* 03:30:00 Asia/Seoul'
write_timer cleanup '*-*-* 04:00:00 Asia/Seoul'
write_timer check '*:0/5'

cat > /etc/logrotate.d/fleaflea-operations <<'EOF'
/var/log/fleaflea/operations.log {
    daily
    rotate 7
    size 10M
    compress
    missingok
    notifempty
    copytruncate
    create 0600 root root
}
EOF
install -m 644 "$SOURCE_DIRECTORY/nginx-observability.conf" /etc/nginx/conf.d/fleaflea-observability.conf
nginx -t
systemctl reload nginx
install -m 600 "$SOURCE_DIRECTORY/ops-agent.yaml" /etc/google-cloud-ops-agent/config.yaml
systemctl restart google-cloud-ops-agent
systemctl daemon-reload
systemctl enable --now fleaflea-backup.timer fleaflea-restore.timer fleaflea-cleanup.timer fleaflea-check.timer
