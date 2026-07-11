#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "usage: deploy-backend-ci.sh <backend-jar>" >&2
}

if [[ "$#" -ne 1 ]]; then
  usage
  exit 2
fi

JAR_PATH="$1"
: "${BACKEND_DEPLOY_HOST:?BACKEND_DEPLOY_HOST is required}"
: "${BACKEND_DEPLOY_USER:?BACKEND_DEPLOY_USER is required}"
: "${BACKEND_DEPLOY_PORT:=22}"
: "${BACKEND_DEPLOY_KEY_FILE:?BACKEND_DEPLOY_KEY_FILE is required}"
: "${BACKEND_DEPLOY_KNOWN_HOSTS:?BACKEND_DEPLOY_KNOWN_HOSTS is required}"

if [[ ! -f "$JAR_PATH" || ! -s "$JAR_PATH" ]]; then
  echo "backend jar not found or empty: ${JAR_PATH}" >&2
  exit 2
fi

if [[ -n "${RELEASE_ID:-}" ]]; then
  release_id="$RELEASE_ID"
else
  short_sha="${GITHUB_SHA:-$(git rev-parse HEAD)}"
  short_sha="${short_sha:0:12}"
  release_id="$(date -u +'%Y%m%dT%H%M%SZ')-${short_sha}"
fi

if [[ ! "$release_id" =~ ^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{7,40}$ ]]; then
  echo "invalid release id: ${release_id}" >&2
  exit 2
fi

known_hosts_file="$(mktemp)"
trap 'rm -f "$known_hosts_file"' EXIT
printf '%s\n' "$BACKEND_DEPLOY_KNOWN_HOSTS" > "$known_hosts_file"
chmod 0600 "$known_hosts_file"

ssh_opts=(
  -i "$BACKEND_DEPLOY_KEY_FILE"
  -p "$BACKEND_DEPLOY_PORT"
  -o IdentitiesOnly=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$known_hosts_file"
  -o BatchMode=yes
)

ssh "${ssh_opts[@]}" "${BACKEND_DEPLOY_USER}@${BACKEND_DEPLOY_HOST}" "deploy ${release_id}" < "$JAR_PATH"

if [[ -n "${BACKEND_HEALTH_URL:-}" ]]; then
  health_host="$(printf '%s' "$BACKEND_HEALTH_URL" | sed -E 's#^[a-zA-Z][a-zA-Z0-9+.-]*://([^/:]+).*#\1#')"
  health_port=443
  if [[ "$BACKEND_HEALTH_URL" == http://* ]]; then
    health_port=80
  fi

  curl_args=(-fsS --connect-timeout 5 --max-time 15 --retry 3 --retry-delay 2 --retry-all-errors)
  if [[ "$health_host" =~ ([0-9]{1,3}\.){3}[0-9]{1,3}\.sslip\.io$ ]]; then
    health_ip="${BASH_REMATCH[0]%.sslip.io}"
    curl_args+=(--resolve "${health_host}:${health_port}:${health_ip}")
  fi

  curl "${curl_args[@]}" "$BACKEND_HEALTH_URL" >/dev/null
fi

echo "release_id=${release_id}"
