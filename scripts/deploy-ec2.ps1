<#
.SYNOPSIS
  Pedidos360 — Idempotent EC2 Deployment Script (Windows PowerShell)
.DESCRIPTION
  Obtiene outputs de Terraform, construye el dominio sslip.io, genera el archivo
  .env.production y ejecuta Docker Compose en la EC2 mediante SSH.
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
ssh @SshArgs "ubuntu@$PublicIp" $RemoteSetupCmd

Write-Host "=== [4/6] Generando archivo de entorno de runtime (.env.production) ===" -ForegroundColor Cyan
$PostgresPass = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "postgres_prod_secret_2026" }
$RabbitPass = if ($env:RABBITMQ_PASSWORD) { $env:RABBITMQ_PASSWORD } else { "guest_prod_secret_2026" }
$BffKey = if ($env:BFF_INTERNAL_KEY) { $env:BFF_INTERNAL_KEY } else { "pedidos360-internal-secret-key-prod-2026" }
$JwtSec = if ($env:JWT_SECRET) { $env:JWT_SECRET } else { "pedidos360-super-secret-jwt-key-prod-2026" }

$EnvFileContent = @"
PUBLIC_DOMAIN=$PublicDomain
PUBLIC_URL=$PublicUrl
FRONTEND_ORIGIN=$PublicUrl
BFF_PUBLIC_URL=$BffPublicUrl
POSTGRES_USER=postgres
POSTGRES_PASSWORD=$PostgresPass
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=$RabbitPass
BFF_INTERNAL_KEY=$BffKey
JWT_SECRET=$JwtSec
AZURE_TENANT_ID=a50f6528-499a-4d94-bcad-ed9b200f7c7b
AZURE_API_CLIENT_ID=5febc8e2-ee14-4452-8914-7d237eb5a6f5
CHAT_RETENTION_DAYS=30
"@

$EnvWriteCmd = "cat <<'EOF' > /opt/pedidos360/app/Backend/.env.production`n$EnvFileContent`nEOF"
ssh @SshArgs "ubuntu@$PublicIp" $EnvWriteCmd

Write-Host "=== [5/6] Desplegando pila de contenedores con Docker Compose ===" -ForegroundColor Cyan
$DeployCmd = @"
  cd /opt/pedidos360/app/Backend
  docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d --build
"@
ssh @SshArgs "ubuntu@$PublicIp" $DeployCmd

Write-Host "=== [6/6] Verificando estado de contenedores en EC2 ===" -ForegroundColor Cyan
ssh @SshArgs "ubuntu@$PublicIp" "cd /opt/pedidos360/app/Backend && docker compose ps"

Write-Host "==========================================================================" -ForegroundColor Green
Write-Host " Despliegue completado exitosamente!" -ForegroundColor Green
Write-Host " Acceda a la aplicación en: $PublicUrl" -ForegroundColor Green
Write-Host " Endpoint API Gateway BFF : $BffPublicUrl" -ForegroundColor Green
Write-Host "==========================================================================" -ForegroundColor Green
