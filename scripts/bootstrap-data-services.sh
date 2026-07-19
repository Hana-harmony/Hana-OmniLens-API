#!/usr/bin/env bash
set -euo pipefail

APP_DIR=/opt/hana-omni-connect-api
COMPOSE_FILE="${APP_DIR}/hana-omni-connect-data.yml"

for file in "${COMPOSE_FILE}" "${APP_DIR}/postgres-password" "${APP_DIR}/redis-users.acl"; do
  if [[ ! -s "${file}" ]]; then
    echo "Required data service file is missing: ${file}" >&2
    exit 1
  fi
done

chmod 600 "${APP_DIR}/postgres-password"
# Compose 파일 secret은 호스트 권한을 유지하므로 Redis 그룹에만 읽기 권한을 준다.
sudo chgrp 999 "${APP_DIR}/redis-users.acl"
chmod 640 "${APP_DIR}/redis-users.acl"
if [[ "$(stat -c '%g:%a' "${APP_DIR}/redis-users.acl")" != "999:640" ]]; then
  echo "Redis ACL file permissions are invalid" >&2
  exit 1
fi
docker network inspect hana-omni-connect-internal >/dev/null 2>&1 \
  || docker network create hana-omni-connect-internal >/dev/null
docker compose -f "${COMPOSE_FILE}" pull
docker compose -f "${COMPOSE_FILE}" up -d --remove-orphans

for container in hana-omni-connect-postgres hana-omni-connect-redis; do
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
