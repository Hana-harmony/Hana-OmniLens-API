#!/usr/bin/env bash
set -euo pipefail

HANA_RUNTIME_SECRET_DIR=/opt/hana-runtime
HANA_RUNTIME_ROOT_FILE="${HANA_RUNTIME_SECRET_DIR}/root-secret"

ensure_runtime_root_secret() {
  local owner group lock_file temp_file
  owner="$(id -un)"
  group="$(id -gn)"
  lock_file="${HANA_RUNTIME_SECRET_DIR}/.root-secret.lock"

  sudo install -d -o "${owner}" -g "${group}" -m 0700 "${HANA_RUNTIME_SECRET_DIR}"
  exec 9>"${lock_file}"
  flock -x 9
  if [[ ! -s "${HANA_RUNTIME_ROOT_FILE}" ]]; then
    umask 077
    temp_file="$(mktemp "${HANA_RUNTIME_SECRET_DIR}/.root-secret.XXXXXX")"
    openssl rand -hex 32 > "${temp_file}"
    mv "${temp_file}" "${HANA_RUNTIME_ROOT_FILE}"
  fi
  chmod 600 "${HANA_RUNTIME_ROOT_FILE}" "${lock_file}"
  flock -u 9
}

read_runtime_root_secret() {
  local secret
  [[ -s "${HANA_RUNTIME_ROOT_FILE}" ]] || {
    echo "Runtime root secret is missing" >&2
    return 1
  }
  secret="$(tr -d '\r\n' < "${HANA_RUNTIME_ROOT_FILE}")"
  [[ "${secret}" =~ ^[0-9a-f]{64}$ ]] || {
    echo "Runtime root secret is invalid" >&2
    return 1
  }
  printf '%s' "${secret}"
}

derive_runtime_secret_hex() {
  local label root
  label="${1:?secret label is required}"
  root="$(read_runtime_root_secret)"
  printf '%s' "${label}" \
    | openssl dgst -sha256 -mac HMAC -macopt "hexkey:${root}" -binary \
    | od -An -vtx1 \
    | tr -d ' \n'
}

derive_runtime_secret_base64() {
  local label root
  label="${1:?secret label is required}"
  root="$(read_runtime_root_secret)"
  printf '%s' "${label}" \
    | openssl dgst -sha256 -mac HMAC -macopt "hexkey:${root}" -binary \
    | openssl base64 -A
}
