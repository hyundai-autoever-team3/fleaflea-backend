#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ $EUID -ne 0 || $# -ne 2 ]]; then
    echo "usage: sudo $0 <api-domain> <certificate-contact-email>" >&2
    exit 2
fi

DOMAIN="$1"
CONTACT_EMAIL="$2"
if [[ ! "$DOMAIN" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]+[a-zA-Z0-9]$ ]]; then
    echo "Invalid API domain" >&2
    exit 2
fi
command -v certbot >/dev/null || { echo "Install certbot before running this script" >&2; exit 2; }

PRIVATE_IP="$(curl --fail --silent --show-error --max-time 10 \
    -H 'Metadata-Flavor: Google' \
    http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/ip)"
if [[ ! "$PRIVATE_IP" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
    echo "Unable to determine the VM private IPv4 address" >&2
    exit 2
fi

install -d -m 755 /var/www/letsencrypt/.well-known/acme-challenge
install -d -m 700 /etc/fleaflea/tls

# Keep the deployment health endpoint on loopback port 80.
cat > /etc/nginx/conf.d/fleaflea-acme.conf <<EOF
server {
    listen $PRIVATE_IP:80;
    server_name $DOMAIN;

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/letsencrypt;
    }

    location / {
        return 301 https://$DOMAIN\$request_uri;
    }
}
EOF
chmod 644 /etc/nginx/conf.d/fleaflea-acme.conf
nginx -t
systemctl reload nginx

certbot certonly --webroot -w /var/www/letsencrypt \
    --domain "$DOMAIN" --cert-name "$DOMAIN" --email "$CONTACT_EMAIL" \
    --non-interactive --agree-tos --keep-until-expiring

for CERTIFICATE_FILE in fullchain.pem privkey.pem; do
    TARGET_FILE="/etc/fleaflea/tls/$CERTIFICATE_FILE"
    if [[ -f "$TARGET_FILE" && ! -L "$TARGET_FILE" ]]; then
        cp -p "$TARGET_FILE" "$TARGET_FILE.before-renewal"
    fi
    ln -sfn "/etc/letsencrypt/live/$DOMAIN/$CERTIFICATE_FILE" "$TARGET_FILE"
done

install -d -m 755 /etc/letsencrypt/renewal-hooks/deploy
cat > /etc/letsencrypt/renewal-hooks/deploy/fleaflea-nginx <<'EOF'
#!/bin/sh
set -eu
/usr/sbin/nginx -t
/usr/bin/systemctl reload nginx
EOF
chmod 700 /etc/letsencrypt/renewal-hooks/deploy/fleaflea-nginx

nginx -t
systemctl reload nginx
systemctl enable --now certbot.timer
