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
  [ -z "${POSTGRES_USER}" ]     && MISSING_SECRETS+=("POSTGRES_USER")
  [ -z "${POSTGRES_PASSWORD}" ] && MISSING_SECRETS+=("POSTGRES_PASSWORD")
  [ -z "${RABBITMQ_USER}" ]     && MISSING_SECRETS+=("RABBITMQ_USER")
  [ -z "${RABBITMQ_PASSWORD}" ] && MISSING_SECRETS+=("RABBITMQ_PASSWORD")
  [ -z "${BFF_INTERNAL_KEY}" ]  && MISSING_SECRETS+=("BFF_INTERNAL_KEY")
  [ -z "${JWT_SECRET}" ]        && MISSING_SECRETS+=("JWT_SECRET")

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
echo "=== [1/7] Obteniendo outputs de Terraform desde ${TF_DIR} ==="
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
echo "=== [2/7] Verificando conectividad SSH con ubuntu@${PUBLIC_IP} ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" "echo 'SSH Connection successful.'"

# ------------------------------------------------------------------------------
# Preparar repositorio en EC2
# ------------------------------------------------------------------------------
echo "=== [3/7] Preparando repositorio y directorio en EC2 ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" 'bash -s' <<REMOTE_SETUP
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
REMOTE_SETUP

# ------------------------------------------------------------------------------
# Generar archivo .env.production
# ------------------------------------------------------------------------------
echo "=== [4/7] Generando archivo de entorno de runtime (.env.production) ==="
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

# Generar .env.production en la EC2 (heredoc sin comillas → variables locales se expanden)
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" 'bash -s' <<REMOTE_ENV
cat <<'INNEREOF' > /opt/pedidos360/app/Backend/.env.production
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
INNEREOF
sed -i 's/\r$//' /opt/pedidos360/app/Backend/.env.production
REMOTE_ENV

# ------------------------------------------------------------------------------
# Desplegar pila de contenedores
# ------------------------------------------------------------------------------
echo "=== [5/7] Desplegando pila de contenedores con Docker Compose ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" 'bash -s' <<'REMOTE_DEPLOY'
  cd /opt/pedidos360/app/Backend

  # Función compose reutilizable (definida DENTRO de la sesión remota)
  compose() {
    docker compose \
      -f docker-compose.yml \
      -f docker-compose.ec2.yml \
      --env-file .env.production \
      "$@"
  }

  compose up -d --build --remove-orphans
REMOTE_DEPLOY

# ------------------------------------------------------------------------------
# Verificar salud de la pila con Fail-Fast
# ------------------------------------------------------------------------------
echo "=== [6/7] Verificando salud de la pila con Fail-Fast ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" 'bash -s' <<'REMOTE_HEALTH'
  cd /opt/pedidos360/app/Backend

  compose() {
    docker compose \
      -f docker-compose.yml \
      -f docker-compose.ec2.yml \
      --env-file .env.production \
      "$@"
  }

  echo "Iniciando sondeo de estado y comprobación de salud (timeout: 120s)..."

  MAX_RETRIES=24
  SLEEP_TIME=5
  IS_HEALTHY=0

  for i in $(seq 1 ${MAX_RETRIES}); do
    echo "Comprobación [${i}/${MAX_RETRIES}]..."

    # 1. Fail-fast: algún contenedor exited o dead
    EXITED_CONTAINERS=$(compose ps -a --filter "status=exited" --filter "status=dead" -q)
    if [ -n "${EXITED_CONTAINERS}" ]; then
      echo "=========================================================================="
      echo " [ERROR FATAL] Uno o más contenedores se detuvieron inesperadamente:"
      compose ps -a --filter "status=exited" --filter "status=dead"
      echo "=========================================================================="
      echo "Últimos logs de contenedores caídos:"
      compose logs --tail=50
      exit 1
    fi

    # 2. Conteo de contenedores unhealthy o starting
    UNHEALTHY_COUNT=$(compose ps --filter "health=unhealthy" -q | wc -l)
    STARTING_COUNT=$(compose ps --filter "health=starting" -q | wc -l)

    # 3. Healthcheck BFF vía docker inspect (puerto NO expuesto al host)
    BFF_CONTAINER_ID=$(compose ps -q bff)
    if [ -n "${BFF_CONTAINER_ID}" ]; then
      BFF_HEALTH=$(docker inspect -f '{{.State.Health.Status}}' "${BFF_CONTAINER_ID}" 2>/dev/null || echo "unhealthy")
    else
      BFF_HEALTH="unhealthy"
    fi

    if [ "${STARTING_COUNT}" -eq 0 ] && [ "${UNHEALTHY_COUNT}" -eq 0 ] && [ "${BFF_HEALTH}" = "healthy" ]; then
      echo "-› Todos los contenedores están healthy y BFF está healthy."
      IS_HEALTHY=1
      break
    fi

    sleep ${SLEEP_TIME}
  done

  if [ "${IS_HEALTHY}" -ne 1 ]; then
    echo "=========================================================================="
    echo " [ERROR FATAL] Timeout agotado: los servicios no alcanzaron el estado saludable."
    compose ps
    echo "=========================================================================="
    echo "Logs recientes para diagnóstico:"
    compose logs --tail=60
    exit 1
  fi

  echo "=========================================================================="
  echo " Estado final de los servicios:"
  compose ps
  echo "=========================================================================="
REMOTE_HEALTH

# ------------------------------------------------------------------------------
# Verificaciones post-deploy (Flyway, volúmenes persistentes)
# ------------------------------------------------------------------------------
echo "=== [7/7] Verificaciones post-deploy ==="
ssh ${SSH_OPTS} ubuntu@"${PUBLIC_IP}" 'bash -s' <<REMOTE_POSTDEPLOY
  cd /opt/pedidos360/app/Backend

  compose() {
    docker compose \
      -f docker-compose.yml \
      -f docker-compose.ec2.yml \
      --env-file .env.production \
      "\$@"
  }

  # ── 1. Flyway: verificar migraciones exitosas por servicio ──
  for svc in usuario pedidos productos carrito notificacion analitica chat; do
    echo "--- Verificando Flyway en \${svc} ---"
    FAILED=\$(compose exec -T postgres psql \
      -U "${POSTGRES_USER_VAL}" \
      -d "\${svc}_db" \
      -tAc "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false;" 2>/dev/null || echo "ERROR")
    if [ "\${FAILED}" = "0" ]; then
      echo "Flyway OK en \${svc}"
    else
      echo "[ERROR] Migraciones fallidas o error en Flyway para \${svc} (COUNT=\${FAILED})"
      exit 1
    fi
  done

  # ── 2. Verificar credenciales PostgreSQL en volumen persistente ──
  echo "--- Verificando credenciales PostgreSQL persistentes ---"
  PG_CONTAINER=\$(compose ps -q postgres)
  if [ -n "\${PG_CONTAINER}" ]; then
    docker exec -i "\${PG_CONTAINER}" bash -c \
      "PGPASSWORD='${POSTGRES_PASS_VAL}' psql -h 127.0.0.1 -U '${POSTGRES_USER_VAL}' -d postgres -tAc 'SELECT current_user;'" \
      && echo "PostgreSQL credentials OK" \
      || echo "[WARN] PostgreSQL volume may contain credentials from a previous initialization."
  fi

  # ── 3. Verificar usuarios RabbitMQ en volumen persistente ──
  echo "--- Verificando usuarios RabbitMQ persistentes ---"
  RMQ_CONTAINER=\$(compose ps -q rabbitmq)
  if [ -n "\${RMQ_CONTAINER}" ]; then
    docker exec -i "\${RMQ_CONTAINER}" rabbitmqctl list_users \
      | grep -q "${RABBITMQ_USER_VAL}" \
      && echo "RabbitMQ user '${RABBITMQ_USER_VAL}' OK" \
      || echo "[WARN] RabbitMQ persistent volume contains a different user configuration."
  fi
REMOTE_POSTDEPLOY

echo "=========================================================================="
echo " Despliegue completado y verificado exitosamente!"
echo " Acceda a la aplicación en: ${PUBLIC_URL}"
echo " Endpoint API Gateway BFF : ${BFF_PUBLIC_URL}"
echo "=========================================================================="
