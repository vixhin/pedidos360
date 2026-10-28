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

# ─── Validación de Secretos Obligatorios en Producción (Fail-Fast) ────────────
if ($Environment -eq "prod") {
  $MissingSecrets = @()
  if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD)) { $MissingSecrets += "POSTGRES_PASSWORD" }
  if ([string]::IsNullOrWhiteSpace($env:RABBITMQ_PASSWORD)) { $MissingSecrets += "RABBITMQ_PASSWORD" }
  if ([string]::IsNullOrWhiteSpace($env:BFF_INTERNAL_KEY)) { $MissingSecrets += "BFF_INTERNAL_KEY" }
  if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) { $MissingSecrets += "JWT_SECRET" }

  if ($MissingSecrets.Count -gt 0) {
    Write-Host "==========================================================================" -ForegroundColor Red
    Write-Host " [ERROR FATAL] Entorno 'prod' seleccionado pero faltan secretos obligatorios." -ForegroundColor Red
    Write-Host " Por motivos de seguridad, no se admiten valores por defecto en producción." -ForegroundColor Red
    Write-Host " Defina las siguientes variables de entorno antes de ejecutar el despliegue:" -ForegroundColor Yellow
    foreach ($sec in $MissingSecrets) {
      Write-Host "   - $sec" -ForegroundColor Yellow
    }
    Write-Host "==========================================================================" -ForegroundColor Red
    exit 1
  }
}

Write-Host "=== [1/6] Obteniendo outputs de Terraform desde $TfDir ===" -ForegroundColor Cyan
if (-not (Test-Path $TfDir)) {
  Write-Error "El directorio $TfDir no existe."
}

Push-Location $TfDir
try {
  $PublicIp = (terraform output -raw public_ip 2>$null).Trim()
  $PublicDns = (terraform output -raw public_dns 2>$null).Trim()
} finally {
  Pop-Location
}

if ([string]::IsNullOrWhiteSpace($PublicIp)) {
  Write-Error "No se pudo obtener public_ip desde terraform output. Verifique haber ejecutado 'terraform apply'."
}

$PublicDomain = "$($PublicIp.Replace('.', '-')).sslip.io"
$PublicUrl = "https://$PublicDomain"

Write-Host "-> Elastic IP     : $PublicIp" -ForegroundColor Green
Write-Host "-> AWS Public DNS : $PublicDns" -ForegroundColor Green
Write-Host "-> PUBLIC_DOMAIN  : $PublicDomain" -ForegroundColor Green
Write-Host "-> PUBLIC_URL     : $PublicUrl" -ForegroundColor Green

# Obtener API Gateway Endpoint vía AWS CLI
$ApiGwEndpoint = (aws apigatewayv2 get-apis --query "Items[?Name=='pedidos360-api-gateway-$Environment'].ApiEndpoint" --output text 2>$null).Trim()
if (-not [string]::IsNullOrWhiteSpace($ApiGwEndpoint) -and $ApiGwEndpoint -ne "None") {
  $BffPublicUrl = "$ApiGwEndpoint/api/bff"
  Write-Host "-> BFF_PUBLIC_URL : $BffPublicUrl (API Gateway)" -ForegroundColor Green
} else {
  $BffPublicUrl = "$PublicUrl/api/bff"
  Write-Host "-> BFF_PUBLIC_URL : $BffPublicUrl (Caddy Direct Fallback)" -ForegroundColor Yellow
}

$SshArgs = @("-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null")
if (-not [string]::IsNullOrWhiteSpace($SshKeyPath)) {
  $SshArgs += @("-i", $SshKeyPath)
}

Write-Host "=== [2/6] Verificando conectividad SSH con ubuntu@$PublicIp ===" -ForegroundColor Cyan
ssh @SshArgs "ubuntu@$PublicIp" "echo 'SSH Connection successful.'"

Write-Host "=== [3/6] Preparando repositorio y directorio en EC2 ===" -ForegroundColor Cyan
$RemoteSetupCmd = @"
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
$RemoteSetupCmd = $RemoteSetupCmd -replace "`r", ""
ssh @SshArgs "ubuntu@$PublicIp" $RemoteSetupCmd

Write-Host "=== [4/6] Generando archivo de entorno de runtime (.env.production) ===" -ForegroundColor Cyan
$PostgresUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "postgres" }
$PostgresPass = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "postgres_dev_secret_2026" }
$RabbitUser = if ($env:RABBITMQ_USER) { $env:RABBITMQ_USER } else { "guest" }
$RabbitPass = if ($env:RABBITMQ_PASSWORD) { $env:RABBITMQ_PASSWORD } else { "guest_dev_secret_2026" }
$BffKey = if ($env:BFF_INTERNAL_KEY) { $env:BFF_INTERNAL_KEY } else { "pedidos360-internal-secret-key-dev-2026" }
$JwtSec = if ($env:JWT_SECRET) { $env:JWT_SECRET } else { "pedidos360-super-secret-jwt-key-dev-2026" }
$AzureTenant = if ($env:AZURE_TENANT_ID) { $env:AZURE_TENANT_ID } else { "a50f6528-499a-4d94-bcad-ed9b200f7c7b" }
$AzureClientId = if ($env:AZURE_API_CLIENT_ID) { $env:AZURE_API_CLIENT_ID } else { "5febc8e2-ee14-4452-8914-7d237eb5a6f5" }

$EnvFileContent = @"
PUBLIC_DOMAIN=$PublicDomain
PUBLIC_URL=$PublicUrl
FRONTEND_ORIGIN=$PublicUrl
BFF_PUBLIC_URL=$BffPublicUrl
POSTGRES_USER=$PostgresUser
POSTGRES_PASSWORD=$PostgresPass
RABBITMQ_USER=$RabbitUser
RABBITMQ_PASSWORD=$RabbitPass
BFF_INTERNAL_KEY=$BffKey
JWT_SECRET=$JwtSec
AZURE_TENANT_ID=$AzureTenant
AZURE_API_CLIENT_ID=$AzureClientId
CHAT_RETENTION_DAYS=30
"@
$EnvFileContent = $EnvFileContent -replace "`r", ""

$EnvWriteCmd = "cat <<'EOF' > /opt/pedidos360/app/Backend/.env.production`n$EnvFileContent`nEOF`nsed -i 's/\r$//' /opt/pedidos360/app/Backend/.env.production"
$EnvWriteCmd = $EnvWriteCmd -replace "`r", ""
ssh @SshArgs "ubuntu@$PublicIp" $EnvWriteCmd

Write-Host "=== [5/6] Desplegando pila de contenedores con Docker Compose ===" -ForegroundColor Cyan
$DeployCmd = @"
  cd /opt/pedidos360/app/Backend
  docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d --build --remove-orphans
"@
$DeployCmd = $DeployCmd -replace "`r", ""
ssh @SshArgs "ubuntu@$PublicIp" $DeployCmd

Write-Host "=== [6/6] Verificando salud de la pila con Fail-Fast ===" -ForegroundColor Cyan
$HealthCheckCmd = @'
  cd /opt/pedidos360/app/Backend
  echo "Iniciando sondeo de estado y comprobación de salud (timeout: 120s)..."

  MAX_RETRIES=24
  SLEEP_TIME=5
  IS_HEALTHY=0

  for i in $(seq 1 ${MAX_RETRIES}); do
    echo "Comprobación [${i}/${MAX_RETRIES}]..."

    # 1. Fail-fast inmediato si algún contenedor se detuvo con fallo
    EXITED_CONTAINERS=$(docker compose ps -a --filter "status=exited" --filter "status=dead" -q)
    if [ -n "${EXITED_CONTAINERS}" ]; then
      echo "=========================================================================="
      echo " [ERROR FATAL] Uno o más contenedores se detuvieron inesperadamente:"
      docker compose ps -a --filter "status=exited" --filter "status=dead"
      echo "=========================================================================="
      echo "Últimos logs de contenedores caídos:"
      docker compose logs --tail=50
      exit 1
    fi

    # 2. Conteo de contenedores unhealthy o en proceso de inicio
    UNHEALTHY_COUNT=$(docker compose ps --filter "health=unhealthy" -q | wc -l)
    STARTING_COUNT=$(docker compose ps --filter "health=starting" -q | wc -l)

    # 3. Comprobación HTTP del endpoint de salud del BFF
    BFF_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8090/health 2>/dev/null || echo "000")

    if [ "${STARTING_COUNT}" -eq 0 ] && [ "${UNHEALTHY_COUNT}" -eq 0 ] && [ "${BFF_STATUS}" = "200" ]; then
      echo "-> Todos los contenedores están reportando salud (HEALTHY) y BFF respondió HTTP 200."
      IS_HEALTHY=1
      break
    fi

    sleep ${SLEEP_TIME}
  done

  if [ "${IS_HEALTHY}" -ne 1 ]; then
    echo "=========================================================================="
    echo " [ERROR FATAL] Timeout agotado: los servicios no alcanzaron el estado saludable."
    docker compose ps
    echo "=========================================================================="
    echo "Logs recientes para diagnóstico:"
    docker compose logs --tail=60
    exit 1
  fi

  echo "=========================================================================="
  echo " Estado final de los servicios:"
  docker compose ps
  echo "=========================================================================="
'@
$HealthCheckCmd = $HealthCheckCmd -replace "`r", ""
ssh @SshArgs "ubuntu@$PublicIp" $HealthCheckCmd

Write-Host "==========================================================================" -ForegroundColor Green
Write-Host " Despliegue completado y verificado exitosamente!" -ForegroundColor Green
Write-Host " Acceda a la aplicación en: $PublicUrl" -ForegroundColor Green
Write-Host " Endpoint API Gateway BFF : $BffPublicUrl" -ForegroundColor Green
Write-Host "==========================================================================" -ForegroundColor Green
