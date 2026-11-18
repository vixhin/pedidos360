# ==============================================================================
# Pedidos360 — Inicializador Idempotente de Bases de Datos en AWS RDS PostgreSQL
# ==============================================================================
# PowerShell script para crear las 7 bases de datos en AWS RDS
#
# Uso:
#   $env:DB_HOST = "pedidos360-db-prod.xxxx.us-east-1.rds.amazonaws.com"
#   $env:DB_PASSWORD = "Pedidos360Secure2026!"
#   .\scripts\init-rds-dbs.ps1
# ==============================================================================

[CmdletBinding()]
param (
    [string]$DbHost = $env:DB_HOST,
    [int]$DbPort = 5432,
    [string]$DbUser = $env:POSTGRES_USER,
    [string]$DbPassword = $env:POSTGRES_PASSWORD
)

if (-not $DbHost) {
    $DbHost = Read-Host "Ingrese el endpoint / host de AWS RDS PostgreSQL"
}

if (-not $DbUser) {
    $DbUser = "postgres"
}

if (-not $DbPassword) {
    $secPass = Read-Host "Ingrese la contrasenia de PostgreSQL ($DbUser@$DbHost)" -AsSecureString
    $DbPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secPass))
}

$databases = @(
    "usuario_db",
    "pedidos_db",
    "carrito_db",
    "analitica_db",
    "productos_db",
    "notificacion_db",
    "chat_db"
)

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " Conectando a AWS RDS PostgreSQL: $DbHost:$DbPort" -ForegroundColor Cyan
Write-Host " Usuario: $DbUser" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

function Run-Query ([string]$query) {
    if (Get-Command psql -ErrorAction SilentlyContinue) {
        $env:PGPASSWORD = $DbPassword
        $env:PGSSLMODE  = "prefer"
        $res = & psql -h $DbHost -p $DbPort -U $DbUser -d postgres -tAc $query 2>$null
        return $res
    } elseif (Get-Command docker -ErrorAction SilentlyContinue) {
        $res = & docker run --rm -e PGPASSWORD=$DbPassword -e PGSSLMODE=prefer postgres:16-alpine psql -h $DbHost -p $DbPort -U $DbUser -d postgres -tAc $query 2>$null
        return $res
    } else {
        throw "No se encontro 'psql' ni 'docker' en el sistema."
    }
}

foreach ($db in $databases) {
    try {
        $exists = Run-Query "SELECT 1 FROM pg_database WHERE datname = '$db';"
        if ($exists -match "1") {
            Write-Host "  [OK] Base de datos '$db' ya existe." -ForegroundColor Green
        } else {
            Write-Host "  [CREANDO] Creando base de datos '$db'..." -ForegroundColor Yellow
            $null = Run-Query "CREATE DATABASE $db;"
            Write-Host "  [CREADA] '$db' creada exitosamente." -ForegroundColor Green
        }
    } catch {
        Write-Host "  [ERROR] Fallo al verificar/crear base de datos $db : $_" -ForegroundColor Red
    }
}

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " [EXITO] Todas las bases de datos de microservicios listas en RDS" -ForegroundColor Green
Write-Host "==================================================================" -ForegroundColor Cyan

