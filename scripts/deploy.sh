#!/usr/bin/env bash
# EC2 deploy: ECR pull, Secrets Manager env, docker run. See docs/aws-setup.ja.md.
#   ./scripts/deploy.sh <image-tag>
set -euo pipefail

if [ "${1:-}" = "" ]; then
  echo "Usage: $0 <image-tag>" >&2
  echo "Example: $0 abc1234" >&2
  exit 1
fi

for cmd in aws curl docker jq; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}" >&2
    exit 1
  fi
done

IMAGE_TAG="$1"
REGION="${AWS_REGION:-ap-northeast-3}"
SECRET_ID="${STARJ_SECRET_ID:-starj/app/production}"
CONTAINER_NAME="${STARJ_CONTAINER_NAME:-starj}"
IMAGE_REPO="${STARJ_IMAGE_REPO:-turtton/starj}"
HOST_PORT="${STARJ_HOST_PORT:-8080}"

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
IMAGE_URI="${ECR_REGISTRY}/${IMAGE_REPO}:${IMAGE_TAG}"

ENV_FILE=$(mktemp)
chmod 600 "${ENV_FILE}"
trap 'rm -f "${ENV_FILE}"' EXIT

echo "==> Fetching secrets from ${SECRET_ID}"
aws secretsmanager get-secret-value \
  --region "${REGION}" \
  --secret-id "${SECRET_ID}" \
  --query SecretString \
  --output text \
| jq -r 'to_entries[] | "\(.key)=\(.value)"' >> "${ENV_FILE}"

echo "SPRING_PROFILES_ACTIVE=production" >> "${ENV_FILE}"

echo "==> Logging in to ECR (${ECR_REGISTRY})"
aws ecr get-login-password --region "${REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

echo "==> Pulling ${IMAGE_URI}"
docker pull "${IMAGE_URI}"

echo "==> Replacing container ${CONTAINER_NAME}"
docker rm -f "${CONTAINER_NAME}" 2>/dev/null || true

docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  -p "${HOST_PORT}:8080" \
  --env-file "${ENV_FILE}" \
  "${IMAGE_URI}"

echo "==> Waiting for health (http://127.0.0.1:${HOST_PORT}/health)"
for i in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:${HOST_PORT}/health" >/dev/null 2>&1; then
    echo "OK"
    exit 0
  fi
  sleep 2
done

echo "Health check timed out. Recent logs:" >&2
docker logs --tail 50 "${CONTAINER_NAME}" >&2 || true
exit 1
