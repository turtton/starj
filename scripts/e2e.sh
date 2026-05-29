#!/usr/bin/env bash
# Single-command UI E2E: real backend (MySQL/Redis/MinIO via podman compose) +
# the packaged Spring Boot jar. Playwright drives Chromium against the app.
#
# Run inside the Nix dev shell so `vp`, `java`, node and podman are on PATH:
#   ./scripts/e2e.sh                  # full run
#   ./scripts/e2e.sh --headed         # extra args are forwarded to `playwright test`
#   KEEP_INFRA=1 ./scripts/e2e.sh     # leave containers running afterwards
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

# Load .env so the app (datasource) and compose (MinIO/MySQL) share settings.
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

INFRA_SERVICES=(mysql redis minio)

cleanup() {
  if [ "${KEEP_INFRA:-0}" = "1" ]; then
    echo "==> KEEP_INFRA=1: leaving containers running"
    return
  fi
  echo "==> Tearing down infra (named volumes are preserved)"
  # podman-compose teardown is flaky (it can bail on the one-shot minio-init
  # container and leave the others running), so remove by fixed name as well.
  # Named data volumes are not touched, so DB/object state persists.
  podman compose down >/dev/null 2>&1 || true
  podman rm -f starj-mysql starj-redis starj-minio >/dev/null 2>&1 || true
}
trap cleanup EXIT

wait_for() { # <service> <cmd...>
  local svc="$1"; shift
  echo "==> Waiting for $svc"
  for _ in $(seq 1 90); do
    if podman compose exec -T "$svc" "$@" >/dev/null 2>&1; then
      echo "    $svc ready"
      return 0
    fi
    sleep 2
  done
  echo "ERROR: $svc did not become ready in time" >&2
  return 1
}

echo "==> Starting infra: ${INFRA_SERVICES[*]}"
podman compose up -d "${INFRA_SERVICES[@]}"

wait_for mysql mysqladmin ping -h localhost
wait_for redis redis-cli ping

echo "==> Ensuring MinIO bucket"
podman compose run --rm minio-init

echo "==> Building boot jar (SPA + backend)"
./gradlew bootJar

echo "==> Running Playwright E2E"
cd "$ROOT/frontend"
node_modules/.bin/playwright test "$@"
