#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR=/var/backups/hana-omni-connect/postgresql
CONTAINER=hana-omni-connect-postgres
RETENTION_DAYS=14
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="${BACKUP_DIR}/omni-connect-${timestamp}.dump"
temporary="${target}.tmp"

install -d -o root -g root -m 0700 "${BACKUP_DIR}"
trap 'rm -f "${temporary}"' EXIT

docker exec -u postgres "${CONTAINER}" \
  pg_dump --username omni_connect_app --dbname omni-connect --format=custom --compress=9 \
  > "${temporary}"
chmod 0600 "${temporary}"
mv "${temporary}" "${target}"
find "${BACKUP_DIR}" -type f -name 'omni-connect-*.dump' -mtime "+${RETENTION_DAYS}" -delete
