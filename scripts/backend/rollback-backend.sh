#!/usr/bin/env bash
set -Eeuo pipefail

: "${BACKEND_DEPLOY_HOST:?BACKEND_DEPLOY_HOST is required}"
: "${BACKEND_DEPLOY_USER:=recetas-deploy}"
: "${BACKEND_DEPLOY_PORT:=22}"
: "${BACKEND_DEPLOY_KEY_FILE:?BACKEND_DEPLOY_KEY_FILE is required}"

known_hosts_file=""
cleanup() {
  if [[ -n "$known_hosts_file" ]]; then
    rm -f "$known_hosts_file"
  fi
}
trap cleanup EXIT

ssh_opts=(
  -i "$BACKEND_DEPLOY_KEY_FILE"
  -p "$BACKEND_DEPLOY_PORT"
  -o IdentitiesOnly=yes
  -o StrictHostKeyChecking=yes
  -o BatchMode=yes
)

if [[ -n "${BACKEND_DEPLOY_KNOWN_HOSTS:-}" ]]; then
  known_hosts_file="$(mktemp)"
  printf '%s\n' "$BACKEND_DEPLOY_KNOWN_HOSTS" > "$known_hosts_file"
  chmod 0600 "$known_hosts_file"
  ssh_opts+=(-o "UserKnownHostsFile=${known_hosts_file}")
fi

ssh "${ssh_opts[@]}" "${BACKEND_DEPLOY_USER}@${BACKEND_DEPLOY_HOST}" rollback

if [[ -n "${BACKEND_HEALTH_URL:-}" ]]; then
  curl -fsS --max-time 10 "$BACKEND_HEALTH_URL" >/dev/null
fi
