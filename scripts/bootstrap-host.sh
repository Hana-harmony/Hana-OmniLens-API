#!/usr/bin/env bash
set -euo pipefail

if ! command -v apt-get >/dev/null 2>&1; then
  echo "Ubuntu 24.04 LTS host is required" >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1 || ! command -v nginx >/dev/null 2>&1 || ! command -v certbot >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y ca-certificates curl nginx certbot
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
