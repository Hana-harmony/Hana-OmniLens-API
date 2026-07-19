#!/bin/sh
set -eu

: "${PROD_SSH_PASSWORD:?}"
printf '%s\n' "${PROD_SSH_PASSWORD}"
