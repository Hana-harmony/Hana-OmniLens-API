#!/usr/bin/env bash
set -euo pipefail

for port in 80 443; do
  if ! /usr/sbin/iptables -C INPUT -p tcp -m conntrack --ctstate NEW --dport "${port}" -j ACCEPT 2>/dev/null; then
    /usr/sbin/iptables -I INPUT 1 -p tcp -m conntrack --ctstate NEW --dport "${port}" -j ACCEPT
  fi
done
