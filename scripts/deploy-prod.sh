#!/usr/bin/env bash
set -euo pipefail

APP_DIR=/opt/hana-omnilens-api
APP_NAME=hana-omnilens-api
ACTIVE_FILE="${APP_DIR}/active-slot"

set -a
source "${APP_DIR}/deploy.env"
set +a

: "${IMAGE:?IMAGE is required}"
: "${GHCR_TOKEN:?GHCR_TOKEN is required}"

active=green
if [[ -f "${ACTIVE_FILE}" ]]; then
  active="$(<"${ACTIVE_FILE}")"
fi
if [[ "${active}" == blue ]]; then
  inactive=green
  port=18081
else
  inactive=blue
  port=18080
fi

owner="$(printf '%s' "${IMAGE}" | cut -d/ -f2)"
printf '%s' "${GHCR_TOKEN}" | docker login ghcr.io -u "${owner}" --password-stdin
docker pull "${IMAGE}"
docker rm -f "${APP_NAME}-${inactive}" >/dev/null 2>&1 || true
docker run -d \
  --name "${APP_NAME}-${inactive}" \
  --restart unless-stopped \
  --read-only \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --pids-limit 512 \
  --memory 5g \
  --cpus 1.5 \
  --env-file "${APP_DIR}/application.env" \
  --add-host host.docker.internal:host-gateway \
  --tmpfs /tmp:rw,noexec,nosuid,size=512m \
  -p "127.0.0.1:${port}:8080" \
  "${IMAGE}" \
  --spring.profiles.active=prod

ready=false
for _ in $(seq 1 90); do
  if curl --fail --silent --show-error "http://127.0.0.1:${port}/actuator/health" | grep -q '"status":"UP"'; then
    ready=true
    break
  fi
  sleep 2
done
if [[ "${ready}" != true ]]; then
  docker logs --tail 200 "${APP_NAME}-${inactive}"
  docker rm -f "${APP_NAME}-${inactive}" >/dev/null
  exit 1
fi

upstream_path=/etc/nginx/conf.d/hana-omnilens-api-upstream.conf
server_path=/etc/nginx/conf.d/hana-omnilens-api.conf
upstream_tmp="$(mktemp)"
upstream_backup="$(mktemp)"
server_backup="$(mktemp)"
had_upstream=false
had_server=false
if sudo test -f "${upstream_path}"; then
  sudo cat "${upstream_path}" > "${upstream_backup}"
  had_upstream=true
fi
if sudo test -f "${server_path}"; then
  sudo cat "${server_path}" > "${server_backup}"
  had_server=true
fi

restore_nginx() {
  if [[ "${had_upstream}" == true ]]; then
    sudo install -o root -g root -m 0644 "${upstream_backup}" "${upstream_path}"
  else
    sudo rm -f "${upstream_path}"
  fi
  if [[ "${had_server}" == true ]]; then
    sudo install -o root -g root -m 0644 "${server_backup}" "${server_path}"
  else
    sudo rm -f "${server_path}"
  fi
  sudo nginx -t
  sudo systemctl reload nginx
}

abort_switch() {
  restore_nginx || true
  docker rm -f "${APP_NAME}-${inactive}" >/dev/null 2>&1 || true
  rm -f "${upstream_tmp}" "${upstream_backup}" "${server_backup}"
  exit 1
}

printf 'upstream hana_omnilens_api { server 127.0.0.1:%s; keepalive 64; }\n' "${port}" > "${upstream_tmp}"
sudo install -o root -g root -m 0644 "${upstream_tmp}" "${upstream_path}"
sudo install -o root -g root -m 0644 "${APP_DIR}/hana-omnilens-api.conf" "${server_path}"
if ! sudo nginx -t; then
  abort_switch
fi
if ! sudo systemctl reload nginx; then
  abort_switch
fi
if ! curl --fail --silent --show-error https://api.hanaomilens.cloud/actuator/health >/dev/null; then
  abort_switch
fi
rm -f "${upstream_tmp}" "${upstream_backup}" "${server_backup}"
printf '%s\n' "${inactive}" > "${ACTIVE_FILE}"

if docker ps --format '{{.Names}}' | grep -Fxq "${APP_NAME}-${active}"; then
  docker stop --time 30 "${APP_NAME}-${active}" >/dev/null
  docker rm "${APP_NAME}-${active}" >/dev/null
fi
docker image prune -f >/dev/null
