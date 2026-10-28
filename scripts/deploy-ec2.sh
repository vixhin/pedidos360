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

# ------------------------------------------------------------------------------
# Validación de secretos obligatorios en producción (Fail-Fast temprano)
# ------------------------------------------------------------------------------
if [ "${ENV}" = "prod" ]; then
  MISSING_SECRETS=()
  [ -z "${POSTGRES_USER}" ] && MISSING_SECRETS+=("POSTGRES_USER")
  [ -z "${POSTGRES_PASSWORD}" ] && MISSING_SECRETS+=("POSTGRES_PASSWORD")
  [ -z "${RABBITMQ_USER}" ] && MISSING_SECRETS+=("RABBITMQ_USER")
  [ -z "${BFF_INTERNAL_KEY}" ] && MISSING_SECRETS+=("BFF_INTERNAL_KEY")
  [ -z "${JWT_SECRET}" ] && MISSING_SECRETS+=("JWT_SECRET")

  if [ ${#MISSING_SECRETS[@]} -gt 0 ]; then
    echo "=========================================================================="
    echo " [ERROR FATAL] Entorno 'prod' seleccionado pero faltan secretos obligatorios."
    echo " Por motivos de seguridad, no se admiten valores por defecto en producción."
    echo " Exporte las siguientes variables antes de continuar:"
    for sec in "${MISSING_SECRETS[@]}"; do
      echo "   - ${sec}"
    done
    echo "=========================================================================="
    exit 1
  fi
fi

# ------------------------------------------------------------------------------
# Obtención de outputs de Terraform
# ------------------------------------------------------------------------------
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

# ------------------------------------------------------------------------------
# Resolución opcional de API Gateway
# ------------------------------------------------------------------------------
API_GW_ENDPOINT=$(aws apigatewayv2 get-apis --query "Items[?Name=='pedidos360-api-gateway-${ENV}'].ApiEndpoint" --output text 2>/dev/null || echo "")
if [ -n "${API_GW_ENDPOINT}" ] && [ "${API_GW_ENDPOINT}" != "None" ]; then
  BFF_PUBLIC_URL="${API_GW_ENDPOINT}/api/bff"
  echo "-> BFF_PUBLIC_URL : ${BFF_PUBLIC_URL} (API Gateway)"
else
  BFF_PUBLIC_URL="${PUBLIC_URL}/api/bff"
  echo "-> BFF_PUBLIC_URL : ${BFF_PUBLIC_URL} (Caddy Direct Fallback)"
fi

# ------------------------------------------------------------------------------
# SSH options
# ------------------------------------------------------------------------------
SSH_OPTS="-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"
if [ -n "${SSH_KEY}" ]; then
  SSH_OPTS="${SSH_OPTS} -i ${SSH_KEY}"
fi

# ------------------------------------------------------------------------------
# Verificar conectividad SSH
# ------------------------------------------------------------------------------
echo "=== [2/6] Verificando conectividad SSH con ubuntu@${PUBLIC_IP} ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" "echo 'SSH Connection successful.'"

# ------------------------------------------------------------------------------
# Preparar repositorio en EC2
# ------------------------------------------------------------------------------
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

# ------------------------------------------------------------------------------
# Generar archivo .env.production con validación de variables
# ------------------------------------------------------------------------------
echo "=== [4/6] Generando archivo de entorno de runtime (.env.production) ==="
if [ "${ENV}" = "prod" ]; then
  POSTGRES_USER_VAL="${POSTGRES_USER}"
  POSTGRES_PASS_VAL="${POSTGRES_PASSWORD}"
  RABBITMQ_USER_VAL="${RABBITMQ_USER}"
  RABBITMQ_PASS_VAL="${RABBITMQ_PASSWORD}"
  BFF_KEY_VAL="${BFF_INTERNAL_KEY}"
  JWT_SECRET_VAL="${JWT_SECRET}"
else
  POSTGRES_USER_VAL="${POSTGRES_USER:-postgres}"
  POSTGRES_PASS_VAL="${POSTGRES_PASSWORD:-postgres_dev_secret_2026}"
  RABBITMQ_USER_VAL="${RABBITMQ_USER:-guest}"
  RABBITMQ_PASS_VAL="${RABBITMQ_PASSWORD:-guest_dev_secret_2026}"
  BFF_KEY_VAL="${BFF_INTERNAL_KEY:-pedidos360-internal-secret-key-dev-2026}"
  JWT_SECRET_VAL="${JWT_SECRET:-pedidos360-super-secret-jwt-key-dev-2026}"
fi

ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" "
cat <<EOF > /opt/pedidos360/app/Backend/.env.production
PUBLIC_DOMAIN=${PUBLIC_DOMAIN}
PUBLIC_URL=${PUBLIC_URL}
FRONTEND_ORIGIN=${PUBLIC_URL}
BFF_PUBLIC_URL=${BFF_PUBLIC_URL}
POSTGRES_USER=${POSTGRES_USER_VAL}
POSTGRES_PASSWORD=${POSTGRES_PASS_VAL}
RABBITMQ_USER=${RABBITMQ_USER_VAL}
RABBITMQ_PASSWORD=${RABBITMQ_PASS_VAL}
BFF_INTERNAL_KEY=${BFF_KEY_VAL}
JWT_SECRET=${JWT_SECRET_VAL}
AZURE_TENANT_ID=${AZURE_TENANT_ID:-a50f6528-499a-4d94-bcad-ed9b200f7c7b}
AZURE_API_CLIENT_ID=${AZURE_API_CLIENT_ID:-5febc8e2-ee14-4452-8914-7d237eb5a6f5}
CHAT_RETENTION_DAYS=${CHAT_RETENTION_DAYS:-30}
EOF
sed -i 's/\\r$//' /opt/pedidos360/app/Backend/.env.production
"

# ------------------------------------------------------------------------------
# Define comando Compose reutilizable
# ------------------------------------------------------------------------------
COMPOSE_CMD="docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production"

# ------------------------------------------------------------------------------
# Desplegar pila de contenedores
# ------------------------------------------------------------------------------
echo "=== [5/6] Desplegando pila de contenedores con Docker Compose ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" "
  cd /opt/pedidos360/app/Backend
  ${COMPOSE_CMD} up -d --build --remove-orphans
"

# ------------------------------------------------------------------------------
# Verificar salud de la pila con Fail-Fast
# ------------------------------------------------------------------------------
echo "=== [6/6] Verificando salud de la pila con Fail-Fast ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" '\
  cd /opt/pedidos360/app/Backend
  echo "Iniciando sondeo de estado y comprobación de salud (timeout: 120s)..."

  MAX_RETRIES=24
  SLEEP_TIME=5
  IS_HEALTHY=0

  for i in $(seq 1 ${MAX_RETRIES}); do
    echo "Comprobación [${i}/${MAX_RETRIES}]..."

    # 1. Fail-fast inmediato si algún contenedor se detuvo con fallo
    EXITED_CONTAINERS=$(${COMPOSE_CMD} ps -a --filter "status=exited" --filter "status=dead" -q)
    if [ -n "${EXITED_CONTAINERS}" ]; then
      echo "=========================================================================="
      echo " [ERROR FATAL] Uno o más contenedores se detuvieron inesperadamente:"
      ${COMPOSE_CMD} ps -a --filter "status=exited" --filter "status=dead"
      echo "=========================================================================="
      echo "Últimos logs de contenedores caídos:"
      ${COMPOSE_CMD} logs --tail=50
      exit 1
    fi

    # 2. Conteo de contenedores unhealthy o en proceso de inicio
    UNHEALTHY_COUNT=$(${COMPOSE_CMD} ps --filter "health=unhealthy" -q | wc -l)
    STARTING_COUNT=$(${COMPOSE_CMD} ps --filter "health=starting" -q | wc -l)

    # 3. Comprobación health del BFF mediante docker inspect
    BFF_CONTAINER_ID=$(${COMPOSE_CMD} ps -q pedidos360-bff)
    if [ -n "${BFF_CONTAINER_ID}" ]; then
      BFF_HEALTH=$(${COMPOSE_CMD} exec -T pedidos360-bff cat /dev/null 2>/dev/null && docker inspect -f '{{.State.Health.Status}}' ${BFF_CONTAINER_ID} || echo "unhealthy")
    else
      BFF_HEALTH="unhealthy"
    fi

    if [ "${STARTING_COUNT}" -eq 0 ] && [ "${UNHEALTHY_COUNT}" -eq 0 ] && [ "${BFF_HEALTH}" = "healthy" ]; then
      echo "-› Todos los contenedores están reportando salud (HEALTHY) y BFF está healthy."
      IS_HEALTHY=1
      break
    fi

    sleep ${SLEEP_TIME}
  done

  if [ "${IS_HEALTHY}" -ne 1 ]; then
    echo "=========================================================================="
    echo " [ERROR FATAL] Timeout agotado: los servicios no alcanzaron el estado saludable."
    ${COMPOSE_CMD} ps
    echo "=========================================================================="
    echo "Logs recientes para diagnóstico:"
    ${COMPOSE_CMD} logs --tail=60
    exit 1
  fi

  echo "=========================================================================="
  echo " Estado final de los servicios:"
  ${COMPOSE_CMD} ps
  echo "=========================================================================="
'

# ------------------------------------------------------------------------------
# Verificaciones post‑deploy (Flyway, volúmenes persistentes)
# ------------------------------------------------------------------------------
echo "=== [7/7] Verificaciones post‑deploy ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" "
  cd /opt/pedidos360/app/Backend
  # 1. Verificar que Flyway haya aplicado todas las migraciones (basado en tabla schema_history)
  for svc in pedidos usuarios productos carrito notificacion analitica chat; do
  echo "--- Verificando Flyway en ${svc} ---"
  ${COMPOSE_CMD} exec -T postgres bash -c "psql -U \"${POSTGRES_USER_VAL}\" -d \"${svc}_db\" -c \"SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE;\" 2>/dev/null | grep -q '^0$' && echo 'Flyway OK' || { echo 'Flyway migrations pending/failure in ${svc}'; exit 1; }"
done
  done

  # 2. Verificación de credenciales en volumen PostgreSQL
  echo "--- Verificando credenciales PostgreSQL persistentes ---"
  POSTGRES_CONTAINER=$( ${COMPOSE_CMD} ps -q postgres )
  if [ -n "${POSTGRES_CONTAINER}" ]; then
    docker exec -i ${POSTGRES_CONTAINER} psql -U "${POSTGRES_USER_VAL}" -d postgres -c "SELECT current_user;" || echo "PostgreSQL volume may contain credentials from a previous initialization."
  fi

  # 3. Verificación de credenciales RabbitMQ persistentes
  echo "--- Verificando usuarios RabbitMQ persistentes ---"
  RABBIT_CONTAINER=$( ${COMPOSE_CMD} ps -q rabbitmq )
  if [ -n "${RABBIT_CONTAINER}" ]; then
    docker exec -i ${RABBIT_CONTAINER} rabbitmqctl list_users | grep "${RABBITMQ_USER_VAL}" || echo "RabbitMQ persistent volume contains a different user configuration."
  fi
"

echo "=========================================================================="
echo " Despliegue completado y verificado exitosamente!"
echo " Acceda a la aplicación en: ${PUBLIC_URL}"
echo " Endpoint API Gateway BFF : ${BFF_PUBLIC_URL}"
echo "=========================================================================="
