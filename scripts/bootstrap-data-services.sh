#!/usr/bin/env bash
set -euo pipefail

APP_DIR=/opt/hana-omnilens-api
COMPOSE_FILE="${APP_DIR}/hana-omnilens-data.yml"

for file in "${COMPOSE_FILE}" "${APP_DIR}/postgres-password" "${APP_DIR}/redis-users.acl"; do
  if [[ ! -s "${file}" ]]; then
    echo "Required data service file is missing: ${file}" >&2
    exit 1
  fi
done

chmod 600 "${APP_DIR}/postgres-password" "${APP_DIR}/redis-users.acl"
docker compose -f "${COMPOSE_FILE}" pull
docker compose -f "${COMPOSE_FILE}" up -d --remove-orphans

for container in hana-omnilens-postgres hana-omnilens-redis; do
  ready=false
  for _ in $(seq 1 60); do
    if [[ "$(docker inspect --format '{{.State.Health.Status}}' "${container}" 2>/dev/null || true)" == healthy ]]; then
      ready=true
      break
    fi
    sleep 2
  done
  if [[ "${ready}" != true ]]; then
    docker logs --tail 200 "${container}" || true
    exit 1
  fi
done
