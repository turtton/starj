#!/usr/bin/env bash
set -euo pipefail

DEPLOY_ROOT="/opt/starj/deploy"
TAG_FILE="${DEPLOY_ROOT}/image_tag.txt"
DEPLOY_SCRIPT="${DEPLOY_ROOT}/scripts/deploy.sh"

if [ ! -f "${TAG_FILE}" ]; then
  echo "image_tag.txt not found: ${TAG_FILE}" >&2
  exit 1
fi

IMAGE_TAG=$(tr -d "[:space:]" < "${TAG_FILE}")

if [ -z "${IMAGE_TAG}" ]; then
  echo "image_tag.txt is empty" >&2
  exit 1
fi

if [ ! -x "${DEPLOY_SCRIPT}" ]; then
  echo "deploy script not executable: ${DEPLOY_SCRIPT}" >&2
  exit 1
fi

echo "==> Deploying image tag: ${IMAGE_TAG}"
"${DEPLOY_SCRIPT}" "${IMAGE_TAG}"
