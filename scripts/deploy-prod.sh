#!/usr/bin/env bash
set -euo pipefail

APP_DIR=/opt/hana-omni-connect-api
APP_NAME=hana-omni-connect-api
APP_PORT=18080
NETWORK=hana-omni-connect-internal
RUNTIME_APP_ENV="${APP_DIR}/runtime-application.env"

source "${APP_DIR}/deploy.env"
source "${APP_DIR}/runtime-secrets.sh"

: "${IMAGE:?IMAGE is required}"
: "${GHCR_USERNAME:?GHCR_USERNAME is required}"
: "${GHCR_TOKEN:?GHCR_TOKEN is required}"

write_runtime_secret_env() {
  local portal_session_key portal_encryption_key term_salt ai_token temp_file
  portal_session_key="$(derive_runtime_secret_hex 'hana/omni-connect/portal-session-signing/v1')"
  portal_encryption_key="$(derive_runtime_secret_base64 'hana/omni-connect/portal-api-key-encryption/v1')"
  term_salt="$(derive_runtime_secret_hex 'hana/omni-connect/term-analytics-hash/v1')"
  ai_token="$(derive_runtime_secret_hex 'hana/ai/maintenance-auth/v1')"
  temp_file="$(mktemp "${APP_DIR}/.runtime-application.XXXXXX")"

  umask 077
  printf '%s\n' \
    "OMNI_CONNECT_PORTAL_SESSION_SIGNING_KEY=${portal_session_key}" \
    "OMNI_CONNECT_PORTAL_API_KEY_ENCRYPTION_KEY=${portal_encryption_key}" \
    "OMNI_CONNECT_TERM_ANALYTICS_HASH_SALT=${term_salt}" \
    "HANNAH_AI_MAINTENANCE_TOKEN=${ai_token}" \
    > "${temp_file}"
  chmod 600 "${temp_file}"
  mv "${temp_file}" "${RUNTIME_APP_ENV}"
}

write_runtime_secret_env

run_container() {
  local image="$1"
  docker run -d \
    --name "${APP_NAME}" \
    --restart unless-stopped \
    --read-only \
    --cap-drop ALL \
    --security-opt no-new-privileges:true \
    --pids-limit 512 \
    --memory 3g \
    --cpus 1.25 \
    --env-file "${APP_DIR}/application.env" \
    --env-file "${RUNTIME_APP_ENV}" \
    --network "${NETWORK}" \
    --tmpfs /tmp:rw,noexec,nosuid,size=512m \
    -p "127.0.0.1:${APP_PORT}:8080" \
    "${image}" \
    --spring.profiles.active=prod
}

wait_until_ready() {
  for _ in $(seq 1 90); do
    if curl --fail --silent --show-error "http://127.0.0.1:${APP_PORT}/actuator/health" \
      | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 2
  done
  return 1
}

stop_container() {
  if docker container inspect "${APP_NAME}" >/dev/null 2>&1; then
    docker stop --time 30 "${APP_NAME}" >/dev/null || true
    docker rm "${APP_NAME}" >/dev/null 2>&1 || true
  fi
}

previous_image="$(docker inspect --format '{{.Config.Image}}' "${APP_NAME}" 2>/dev/null || true)"

rollback() {
  docker logs --tail 200 "${APP_NAME}" 2>/dev/null || true
  docker rm -f "${APP_NAME}" >/dev/null 2>&1 || true
  if [[ -n "${previous_image}" ]]; then
    run_container "${previous_image}" >/dev/null
    wait_until_ready || true
  fi
  exit 1
}

install_nginx_config() {
  local upstream_path=/etc/nginx/conf.d/hana-omni-connect-api-upstream.conf
  local server_path=/etc/nginx/conf.d/hana-omni-connect-api.conf
  local backup_dir
  local had_upstream=false
  local had_server=false
  backup_dir="$(mktemp -d)"

  # 이전 배포 방식의 활성 설정을 제거해 server_name 중복을 방지한다.
  sudo rm -f \
    /etc/nginx/conf.d/hana-omni-connect-api-bootstrap.conf \
    /etc/nginx/sites-enabled/hana-omni-connect-api \
    /etc/nginx/sites-enabled/hana-omni-connect-api.conf

  if sudo test -f "${upstream_path}"; then
    sudo cat "${upstream_path}" > "${backup_dir}/upstream.conf"
    had_upstream=true
  fi
  if sudo test -f "${server_path}"; then
    sudo cat "${server_path}" > "${backup_dir}/server.conf"
    had_server=true
  fi

  printf 'upstream hana_omni_connect_api { server 127.0.0.1:%s; keepalive 64; }\n' "${APP_PORT}" > "${backup_dir}/new-upstream.conf"
  if ! sudo install -o root -g root -m 0644 "${backup_dir}/new-upstream.conf" "${upstream_path}" \
    || ! sudo install -o root -g root -m 0644 "${APP_DIR}/hana-omni-connect-api.conf" "${server_path}" \
    || ! sudo nginx -t; then
    if [[ "${had_upstream}" == true ]]; then
      sudo install -o root -g root -m 0644 "${backup_dir}/upstream.conf" "${upstream_path}"
    else
      sudo rm -f "${upstream_path}"
    fi
    if [[ "${had_server}" == true ]]; then
      sudo install -o root -g root -m 0644 "${backup_dir}/server.conf" "${server_path}"
    else
      sudo rm -f "${server_path}"
    fi
    sudo nginx -t >/dev/null 2>&1 || true
    rm -rf "${backup_dir}"
    return 1
  fi

  rm -rf "${backup_dir}"
}

docker network inspect "${NETWORK}" >/dev/null 2>&1 \
  || docker network create "${NETWORK}" >/dev/null
printf '%s' "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USERNAME}" --password-stdin
docker pull "${IMAGE}"

install_nginx_config

stop_container
run_container "${IMAGE}" >/dev/null || rollback
wait_until_ready || rollback

sudo systemctl reload nginx || rollback
curl --fail --silent --show-error https://api.hanaomni.cloud/actuator/health >/dev/null || rollback
docker image prune -f >/dev/null
