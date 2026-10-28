<#
.SYNOPSIS
  Pedidos360 — Idempotent EC2 Deployment Script (Windows PowerShell)
.DESCRIPTION
  Obtiene outputs de Terraform, construye el dominio sslip.io, genera el archivo
  .env.production y ejecuta Docker Compose en la EC2 mediante SSH con validación
  fail-fast de salud de contenedores y manejo de saltos de línea LF.
.EXAMPLE
  .\scripts\deploy-ec2.ps1 -Environment dev -SshKeyPath "C:\Users\user\.ssh\pedidos360-dev-key.pem"
#>

[CmdletBinding()]
param (
  [string]$Environment = "dev",
  [string]$SshKeyPath = ""
)

$ErrorActionPreference = "Stop"

$RepoUrl = "https://github.com/vixhin/pedidos360.git"
$TfDir = "terraform/environments/$Environment"

# ------------------------------------------------------------------------------
# Validación de secretos obligatorios en producción (Fail-Fast)
# ------------------------------------------------------------------------------
if ($Environment -eq "prod") {
  $MissingSecrets = @()
  if ([string]::IsNullOrWhiteSpace($env:POSTGRES_USER))     { $MissingSecrets += "POSTGRES_USER" }
  if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD))  { $MissingSecrets += "POSTGRES_PASSWORD" }
  if ([string]::IsNullOrWhiteSpace($env:RABBITMQ_USER))      { $MissingSecrets += "RABBITMQ_USER" }
  if ([string]::IsNullOrWhiteSpace($env:RABBITMQ_PASSWORD))  { $MissingSecrets += "RABBITMQ_PASSWORD" }
  if ([string]::IsNullOrWhiteSpace($env:BFF_INTERNAL_KEY))   { $MissingSecrets += "BFF_INTERNAL_KEY" }
  if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET))         { $MissingSecrets += "JWT_SECRET" }

  if ($MissingSecrets.Count -gt 0) {
    Write-Host "==========================================================================" -ForegroundColor Red
    Write-Host " [ERROR FATAL] Entorno 'prod' seleccionado pero faltan secretos obligatorios." -ForegroundColor Red
    Write-Host " Por motivos de seguridad, no se admiten valores por defecto en producción." -ForegroundColor Red
    Write-Host " Defina las siguientes variables de entorno antes de ejecutar el despliegue:" -ForegroundColor Yellow
    foreach ($sec in $MissingSecrets) { Write-Host "   - $sec" -ForegroundColor Yellow }
    Write-Host "==========================================================================" -ForegroundColor Red
    exit 1
  }
}

# ------------------------------------------------------------------------------
# Obtención de outputs de Terraform
# ------------------------------------------------------------------------------
Write-Host "=== [1/7] Obteniendo outputs de Terraform desde $TfDir ===" -ForegroundColor Cyan
if (-not (Test-Path $TfDir)) { Write-Error "El directorio $TfDir no existe." }

Push-Location $TfDir
try {
  $PublicIp  = (terraform output -raw public_ip 2>$null).Trim()
  $PublicDns = (terraform output -raw public_dns 2>$null).Trim()
} finally { Pop-Location }

if ([string]::IsNullOrWhiteSpace($PublicIp)) {
  Write-Error "No se pudo obtener public_ip desde terraform output. Verifique haber ejecutado 'terraform apply'."
}

$PublicDomain = "$($PublicIp.Replace('.', '-')).sslip.io"
$PublicUrl    = "https://$PublicDomain"

Write-Host "-> Elastic IP     : $PublicIp"  -ForegroundColor Green
Write-Host "-> AWS Public DNS : $PublicDns" -ForegroundColor Green
Write-Host "-> PUBLIC_DOMAIN  : $PublicDomain" -ForegroundColor Green
Write-Host "-> PUBLIC_URL     : $PublicUrl" -ForegroundColor Green

# ------------------------------------------------------------------------------
# Resolución opcional de API Gateway
# ------------------------------------------------------------------------------
$ApiGwEndpoint = (aws apigatewayv2 get-apis --query "Items[?Name=='pedidos360-api-gateway-$Environment'].ApiEndpoint" --output text 2>$null).Trim()
if (-not [string]::IsNullOrWhiteSpace($ApiGwEndpoint) -and $ApiGwEndpoint -ne "None") {
  $BffPublicUrl = "$ApiGwEndpoint/api/bff"
  Write-Host "-> BFF_PUBLIC_URL : $BffPublicUrl (API Gateway)" -ForegroundColor Green
} else {
  $BffPublicUrl = "$PublicUrl/api/bff"
  Write-Host "-> BFF_PUBLIC_URL : $BffPublicUrl (Caddy Direct Fallback)" -ForegroundColor Yellow
}

# ------------------------------------------------------------------------------
# SSH options
# ------------------------------------------------------------------------------
$SshArgs = @("-o","StrictHostKeyChecking=no","-o","UserKnownHostsFile=/dev/null")
if (-not [string]::IsNullOrWhiteSpace($SshKeyPath)) { $SshArgs += @("-i",$SshKeyPath) }

# ------------------------------------------------------------------------------
# Verificar conectividad SSH
# ------------------------------------------------------------------------------
Write-Host "=== [2/7] Verificando conectividad SSH con ubuntu@$PublicIp ===" -ForegroundColor Cyan
ssh @SshArgs "ubuntu@$PublicIp" "echo 'SSH Connection successful.'"

# ------------------------------------------------------------------------------
# Preparar repositorio en EC2
# ------------------------------------------------------------------------------
Write-Host "=== [3/7] Preparando repositorio y directorio en EC2 ===" -ForegroundColor Cyan
$RemoteSetupScript = @"
sudo mkdir -p /opt/pedidos360
sudo chown -R ubuntu:ubuntu /opt/pedidos360
if [ ! -d /opt/pedidos360/app/.git ]; then
  echo 'Clonando repositorio...'
  git clone $RepoUrl /opt/pedidos360/app
else
  echo 'Actualizando repositorio existente...'
  cd /opt/pedidos360/app
  git fetch --all
  git checkout main
  git pull origin main
fi
"@
$RemoteSetupScript = $RemoteSetupScript -replace "`r", ""
ssh @SshArgs "ubuntu@$PublicIp" $RemoteSetupScript

# ------------------------------------------------------------------------------
# Generar .env.production
# ------------------------------------------------------------------------------
Write-Host "=== [4/7] Generando archivo .env.production ===" -ForegroundColor Cyan

if ($Environment -eq "prod") {
  $PgUser   = $env:POSTGRES_USER
  $PgPass   = $env:POSTGRES_PASSWORD
  $RmqUser  = $env:RABBITMQ_USER
  $RmqPass  = $env:RABBITMQ_PASSWORD
  $BffKey   = $env:BFF_INTERNAL_KEY
  $JwtSec   = $env:JWT_SECRET
} else {
  $PgUser   = if ($env:POSTGRES_USER)     { $env:POSTGRES_USER }     else { "postgres" }
  $PgPass   = if ($env:POSTGRES_PASSWORD)  { $env:POSTGRES_PASSWORD }  else { "postgres_dev_secret_2026" }
  $RmqUser  = if ($env:RABBITMQ_USER)      { $env:RABBITMQ_USER }      else { "guest" }
  $RmqPass  = if ($env:RABBITMQ_PASSWORD)  { $env:RABBITMQ_PASSWORD }  else { "guest_dev_secret_2026" }
  $BffKey   = if ($env:BFF_INTERNAL_KEY)   { $env:BFF_INTERNAL_KEY }   else { "pedidos360-internal-secret-key-dev-2026" }
  $JwtSec   = if ($env:JWT_SECRET)         { $env:JWT_SECRET }         else { "pedidos360-super-secret-jwt-key-dev-2026" }
}
$AzTenant   = if ($env:AZURE_TENANT_ID)    { $env:AZURE_TENANT_ID }    else { "a50f6528-499a-4d94-bcad-ed9b200f7c7b" }
$AzClientId = if ($env:AZURE_API_CLIENT_ID){ $env:AZURE_API_CLIENT_ID }else { "5febc8e2-ee14-4452-8914-7d237eb5a6f5" }

$EnvContent = @"
PUBLIC_DOMAIN=$PublicDomain
PUBLIC_URL=$PublicUrl
FRONTEND_ORIGIN=$PublicUrl
BFF_PUBLIC_URL=$BffPublicUrl
POSTGRES_USER=$PgUser
POSTGRES_PASSWORD=$PgPass
RABBITMQ_USER=$RmqUser
RABBITMQ_PASSWORD=$RmqPass
BFF_INTERNAL_KEY=$BffKey
JWT_SECRET=$JwtSec
AZURE_TENANT_ID=$AzTenant
AZURE_API_CLIENT_ID=$AzClientId
CHAT_RETENTION_DAYS=30
"@
$EnvContent = $EnvContent -replace "`r", ""

$EnvWriteCmd = "cat <<'EOF' > /opt/pedidos360/app/Backend/.env.production`n${EnvContent}`nEOF`nsed -i 's/\r$//' /opt/pedidos360/app/Backend/.env.production"
$EnvWriteCmd = $EnvWriteCmd -replace "`r", ""
ssh @SshArgs "ubuntu@$PublicIp" $EnvWriteCmd

# ------------------------------------------------------------------------------
# Función compose remota (se inyecta en cada bloque SSH como texto literal)
# ------------------------------------------------------------------------------
$ComposeFn = @'
compose() {
  docker compose \
    -f docker-compose.yml \
    -f docker-compose.ec2.yml \
    --env-file .env.production \
    "$@"
}
'@

# ------------------------------------------------------------------------------
# Desplegar stack
# ------------------------------------------------------------------------------
Write-Host "=== [5/7] Desplegando stack con Docker Compose ===" -ForegroundColor Cyan
$DeployScript = @"
cd /opt/pedidos360/app/Backend
$ComposeFn
compose up -d --build --remove-orphans
"@
$DeployScript = $DeployScript -replace "`r", ""
ssh @SshArgs "ubuntu@$PublicIp" $DeployScript

# ------------------------------------------------------------------------------
# Verificar salud (Fail-Fast)
# ------------------------------------------------------------------------------
Write-Host "=== [6/7] Verificando salud de la pila ===" -ForegroundColor Cyan
$HealthScript = @"
cd /opt/pedidos360/app/Backend
$ComposeFn
echo 'Iniciando sondeo de salud (timeout: 120s)...'
MAX_RETRIES=24
SLEEP_TIME=5
IS_HEALTHY=0
for i in `$(seq 1 `$MAX_RETRIES); do
  echo "Comprobación [`$i/`$MAX_RETRIES]..."
  EXITED=`$(compose ps -a --filter "status=exited" --filter "status=dead" -q)
  if [ -n "`$EXITED" ]; then
    echo '=========================================================================='
    echo ' [ERROR FATAL] Contenedores fallidos:'
    compose ps -a --filter "status=exited" --filter "status=dead"
    echo '=========================================================================='
    echo 'Ultimos logs:'
    compose logs --tail=50
    exit 1
  fi
  UNHEALTHY=`$(compose ps --filter "health=unhealthy" -q | wc -l)
  STARTING=`$(compose ps --filter "health=starting" -q | wc -l)
  BFF_ID=`$(compose ps -q bff)
  if [ -n "`$BFF_ID" ]; then
    BFF_HEALTH=`$(docker inspect -f '{{.State.Health.Status}}' "`$BFF_ID" 2>/dev/null || echo "unhealthy")
  else
    BFF_HEALTH="unhealthy"
  fi
  if [ "`$STARTING" -eq 0 ] && [ "`$UNHEALTHY" -eq 0 ] && [ "`$BFF_HEALTH" = "healthy" ]; then
    echo '-> Todos los contenedores saludables y BFF healthy.'
    IS_HEALTHY=1
    break
  fi
  sleep `$SLEEP_TIME
done
if [ "`$IS_HEALTHY" -ne 1 ]; then
  echo '=========================================================================='
  echo ' [ERROR FATAL] Timeout: servicios no saludables.'
  compose ps
  echo '=========================================================================='
  echo 'Logs recientes:'
  compose logs --tail=60
  exit 1
fi
echo '=========================================================================='
echo ' Estado final de los servicios:'
compose ps
echo '=========================================================================='
"@
$HealthScript = $HealthScript -replace "`r", ""
ssh @SshArgs "ubuntu@$PublicIp" $HealthScript

# ------------------------------------------------------------------------------
# Verificaciones post-deploy (Flyway, volúmenes persistentes)
# ------------------------------------------------------------------------------
Write-Host "=== [7/7] Verificaciones post-deploy ===" -ForegroundColor Cyan
$PostDeployScript = @"
cd /opt/pedidos360/app/Backend
$ComposeFn

# Flyway
for svc in usuario pedidos productos carrito notificacion analitica chat; do
  echo "--- Verificando Flyway en `${svc} ---"
  FAILED=`$(compose exec -T postgres psql \
    -U "$PgUser" \
    -d "`${svc}_db" \
    -tAc "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false;" 2>/dev/null || echo "ERROR")
  if [ "`$FAILED" = "0" ]; then
    echo "Flyway OK en `${svc}"
  else
    echo "[ERROR] Migraciones fallidas o error en Flyway para `${svc} (COUNT=`$FAILED)"
    exit 1
  fi
done

# PostgreSQL credential check
echo '--- Verificando credenciales PostgreSQL persistentes ---'
PG_CONTAINER=`$(compose ps -q postgres)
if [ -n "`$PG_CONTAINER" ]; then
  docker exec -i "`$PG_CONTAINER" bash -c \
    "PGPASSWORD='$PgPass' psql -h 127.0.0.1 -U '$PgUser' -d postgres -tAc 'SELECT current_user;'" \
    && echo 'PostgreSQL credentials OK' \
    || echo '[WARN] PostgreSQL volume may contain credentials from a previous initialization.'
fi

# RabbitMQ user check
echo '--- Verificando usuarios RabbitMQ persistentes ---'
RMQ_CONTAINER=`$(compose ps -q rabbitmq)
if [ -n "`$RMQ_CONTAINER" ]; then
  docker exec -i "`$RMQ_CONTAINER" rabbitmqctl list_users \
    | grep -q "$RmqUser" \
    && echo "RabbitMQ user '$RmqUser' OK" \
    || echo '[WARN] RabbitMQ persistent volume contains a different user configuration.'
fi
"@
$PostDeployScript = $PostDeployScript -replace "`r", ""
ssh @SshArgs "ubuntu@$PublicIp" $PostDeployScript

Write-Host "==========================================================================" -ForegroundColor Green
Write-Host " Despliegue completado y verificado exitosamente!" -ForegroundColor Green
Write-Host " Acceda a la aplicación en: $PublicUrl" -ForegroundColor Green
Write-Host " Endpoint API Gateway BFF : $BffPublicUrl" -ForegroundColor Green
Write-Host "==========================================================================" -ForegroundColor Green
