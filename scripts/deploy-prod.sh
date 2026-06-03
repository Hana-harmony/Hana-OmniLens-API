#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/hana-omnilens-api"
APP_NAME="hana-omnilens-api"

set -a
source "${APP_DIR}/deploy-prod.env"
set +a

GHCR_OWNER="$(printf '%s' "${HANA_OMNILENS_IMAGE}" | cut -d/ -f2)"

echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_OWNER}" --password-stdin

docker compose \
  --env-file "${APP_DIR}/application-prod.env" \
  -f "${APP_DIR}/compose.prod.yml" \
  pull api

docker compose \
  --env-file "${APP_DIR}/application-prod.env" \
  -f "${APP_DIR}/compose.prod.yml" \
  up -d api

docker image prune -f

echo "${APP_NAME} deployed"
