#!/usr/bin/env bash
set -euo pipefail

: "${LETSENCRYPT_EMAIL:?LETSENCRYPT_EMAIL is required}"

sudo install -d -m 0755 /var/www/certbot
sudo install -o root -g root -m 0644 \
  /opt/hana-omni-connect-api/bootstrap-http.conf \
  /etc/nginx/conf.d/hana-omni-connect-api-bootstrap.conf
sudo nginx -t
sudo systemctl reload nginx
sudo certbot certonly --webroot \
  --webroot-path /var/www/certbot \
  --domain api.hanaomni.cloud \
  --email "${LETSENCRYPT_EMAIL}" \
  --agree-tos \
  --non-interactive
sudo rm /etc/nginx/conf.d/hana-omni-connect-api-bootstrap.conf
sudo systemctl enable --now certbot.timer
