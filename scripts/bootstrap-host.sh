#!/usr/bin/env bash
set -euo pipefail

if ! command -v apt-get >/dev/null 2>&1; then
  echo "Ubuntu 24.04 LTS host is required" >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1 \
  || ! command -v nginx >/dev/null 2>&1 \
  || ! command -v certbot >/dev/null 2>&1 \
  || ! command -v openssl >/dev/null 2>&1 \
  || ! command -v flock >/dev/null 2>&1 \
  || ! command -v iptables >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y ca-certificates curl nginx certbot openssl util-linux iptables
fi

if ! command -v docker >/dev/null 2>&1; then
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo tee /etc/apt/keyrings/docker.asc >/dev/null
  sudo chmod a+r /etc/apt/keyrings/docker.asc
  . /etc/os-release
  printf '%s\n' \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

sudo usermod -aG docker "$(id -un)"
sudo systemctl enable --now docker nginx certbot.timer

sudo install -o root -g root -m 0750 \
  /opt/hana-omni-connect-api/ensure-web-ingress.sh \
  /usr/local/sbin/hana-omni-connect-web-ingress
sudo install -o root -g root -m 0644 \
  /opt/hana-omni-connect-api/hana-omni-connect-web-ingress.service \
  /etc/systemd/system/hana-omni-connect-web-ingress.service
sudo systemctl daemon-reload
sudo systemctl enable --now hana-omni-connect-web-ingress.service

source /opt/hana-omni-connect-api/runtime-secrets.sh
ensure_runtime_root_secret

sudo install -o root -g root -m 0750 \
  /opt/hana-omni-connect-api/backup-postgres.sh \
  /usr/local/sbin/hana-omni-connect-postgres-backup
sudo install -o root -g root -m 0644 \
  /opt/hana-omni-connect-api/hana-omni-connect-postgres-backup.service \
  /etc/systemd/system/hana-omni-connect-postgres-backup.service
sudo install -o root -g root -m 0644 \
  /opt/hana-omni-connect-api/hana-omni-connect-postgres-backup.timer \
  /etc/systemd/system/hana-omni-connect-postgres-backup.timer
sudo install -d -o root -g root -m 0700 /var/backups/hana-omni-connect/postgresql
sudo systemctl daemon-reload
sudo systemctl enable --now hana-omni-connect-postgres-backup.timer
