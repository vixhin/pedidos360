#!/usr/bin/env bash
# ==============================================================================
# Pedidos360 — Idempotent EC2 Deployment Script (Linux / macOS / Bash)
# ==============================================================================
# Uso:
#   ./scripts/deploy-ec2.sh [dev|prod] [ssh_key_path]
# Ejemplo:
#   ./scripts/deploy-ec2.sh dev ~/.ssh/pedidos360-dev-key.pem
# ==============================================================================

set -eo pipefail

ENV="${1:-dev}"
SSH_KEY="${2:-}"
REPO_URL="https://github.com/vixhin/pedidos360.git"
TF_DIR="terraform/environments/${ENV}"

echo "=== [1/6] Obteniendo outputs de Terraform desde ${TF_DIR} ==="
if [ ! -d "${TF_DIR}" ]; then
  echo "Error: El directorio ${TF_DIR} no existe."
  exit 1
fi

PUBLIC_IP=$(cd "${TF_DIR}" && terraform output -raw public_ip 2>/dev/null || echo "")
PUBLIC_DNS=$(cd "${TF_DIR}" && terraform output -raw public_dns 2>/dev/null || echo "")

if [ -z "${PUBLIC_IP}" ]; then
  echo "Error: No se pudo obtener public_ip desde terraform output."
  echo "Asegúrese de haber ejecutado 'terraform apply' previamente."
  exit 1
fi

PUBLIC_DOMAIN="${PUBLIC_IP//./-}.sslip.io"
PUBLIC_URL="https://${PUBLIC_DOMAIN}"

echo "-> Elastic IP     : ${PUBLIC_IP}"
echo "-> AWS Public DNS : ${PUBLIC_DNS}"
echo "-> PUBLIC_DOMAIN  : ${PUBLIC_DOMAIN}"
echo "-> PUBLIC_URL     : ${PUBLIC_URL}"

# Intentar resolver endpoint de API Gateway vía AWS CLI si está disponible
API_GW_ENDPOINT=$(aws apigatewayv2 get-apis --query "Items[?Name=='pedidos360-api-gateway-${ENV}'].ApiEndpoint" --output text 2>/dev/null || echo "")
if [ -n "${API_GW_ENDPOINT}" ] && [ "${API_GW_ENDPOINT}" != "None" ]; then
  BFF_PUBLIC_URL="${API_GW_ENDPOINT}/api/bff"
  echo "-> BFF_PUBLIC_URL : ${BFF_PUBLIC_URL} (API Gateway)"
else
  BFF_PUBLIC_URL="${PUBLIC_URL}/api/bff"
  echo "-> BFF_PUBLIC_URL : ${BFF_PUBLIC_URL} (Caddy Direct Fallback)"
fi

SSH_OPTS="-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"
if [ -n "${SSH_KEY}" ]; then
  SSH_OPTS="${SSH_OPTS} -i ${SSH_KEY}"
fi

echo "=== [2/6] Verificando conectividad SSH con ubuntu@${PUBLIC_IP} ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" "echo 'SSH Connection successful.'"

echo "=== [3/6] Preparando repositorio y directorio en EC2 ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" "
  sudo mkdir -p /opt/pedidos360
  sudo chown -R ubuntu:ubuntu /opt/pedidos360
  if [ ! -d /opt/pedidos360/app/.git ]; then
    echo 'Clonando repositorio...'
    git clone ${REPO_URL} /opt/pedidos360/app
  else
    echo 'Actualizando repositorio existente...'
    cd /opt/pedidos360/app
    git fetch --all
    git checkout main
    git pull origin main
  fi
"

echo "=== [4/6] Generando archivo de entorno de runtime (.env.production) ==="
POSTGRES_PASS_VAL="${POSTGRES_PASSWORD:-postgres_prod_secret_2026}"
RABBITMQ_PASS_VAL="${RABBITMQ_PASSWORD:-guest_prod_secret_2026}"
BFF_KEY_VAL="${BFF_INTERNAL_KEY:-pedidos360-internal-secret-key-prod-2026}"
JWT_SECRET_VAL="${JWT_SECRET:-pedidos360-super-secret-jwt-key-prod-2026}"

ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" "
cat <<'EOF' > /opt/pedidos360/app/Backend/.env.production
PUBLIC_DOMAIN=${PUBLIC_DOMAIN}
PUBLIC_URL=${PUBLIC_URL}
FRONTEND_ORIGIN=${PUBLIC_URL}
BFF_PUBLIC_URL=${BFF_PUBLIC_URL}
POSTGRES_USER=postgres
POSTGRES_PASSWORD=${POSTGRES_PASS_VAL}
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=${RABBITMQ_PASS_VAL}
BFF_INTERNAL_KEY=${BFF_KEY_VAL}
JWT_SECRET=${JWT_SECRET_VAL}
AZURE_TENANT_ID=a50f6528-499a-4d94-bcad-ed9b200f7c7b
AZURE_API_CLIENT_ID=5febc8e2-ee14-4452-8914-7d237eb5a6f5
CHAT_RETENTION_DAYS=30
EOF
"

echo "=== [5/6] Desplegando pila de contenedores con Docker Compose ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" "
  cd /opt/pedidos360/app/Backend
  docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d --build
"

echo "=== [6/6] Verificando estado de contenedores en EC2 ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" "
  cd /opt/pedidos360/app/Backend
  docker compose ps
"

echo "=========================================================================="
echo " Despliegue completado exitosamente!"
echo " Acceda a la aplicación en: ${PUBLIC_URL}"
echo " Endpoint API Gateway BFF : ${BFF_PUBLIC_URL}"
echo "=========================================================================="
