#!/usr/bin/env bash
set -euo pipefail

APP_DIR=/opt/hana-omnilens-api
ENV_FILE=${APP_DIR}/monitoring.env

test -s "${ENV_FILE}"
chmod 600 "${ENV_FILE}"
docker network inspect hana-omnilens-internal >/dev/null 2>&1 \
  || docker network create hana-omnilens-internal >/dev/null

sudo install -o root -g root -m 0644 \
  "${APP_DIR}/deploy/systemd/hana-omnilens-monitoring.service" \
  /etc/systemd/system/hana-omnilens-monitoring.service
sudo systemctl daemon-reload
sudo systemctl enable hana-omnilens-monitoring.service
sudo systemctl restart hana-omnilens-monitoring.service

for _ in $(seq 1 60); do
  if curl --fail --silent --show-error http://127.0.0.1:3300/api/health >/dev/null; then
    exit 0
  fi
  sleep 2
done

docker compose --env-file "${ENV_FILE}" -f "${APP_DIR}/deploy/monitoring/compose.yml" ps
exit 1
