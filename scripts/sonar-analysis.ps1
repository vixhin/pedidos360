# Análisis estático de Pedidos 360 con SonarQube local.
# Uso (desde la raíz del repo):  powershell -ExecutionPolicy Bypass -File scripts/sonar-analysis.ps1 [-SkipBuild]
# Requisitos: Docker Desktop en ejecución, JDK 21 y Maven (o los mvnw de cada servicio).
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$SonarUrl = "http://localhost:9000"
$TokenFile = Join-Path $Root "sonar/.sonar-token"
$Services = @("usuario", "productos", "carrito", "pedidos", "notificacion", "analitica", "chat", "bff")

Write-Host "==> Levantando SonarQube" -ForegroundColor Cyan
docker compose -f (Join-Path $Root "sonar/docker-compose.yml") up -d
if ($LASTEXITCODE -ne 0) { throw "No se pudo iniciar SonarQube (¿Docker Desktop está abierto?)" }

Write-Host "==> Esperando a que SonarQube esté UP" -ForegroundColor Cyan
$status = ""
for ($i = 0; $i -lt 60 -and $status -ne "UP"; $i++) {
    try { $status = (Invoke-RestMethod "$SonarUrl/api/system/status").status } catch { $status = "" }
    if ($status -ne "UP") { Start-Sleep -Seconds 5 }
}
if ($status -ne "UP") { throw "SonarQube no respondió en $SonarUrl" }

# Token: variable SONAR_TOKEN, archivo local (ignorado por git) o se genera con el admin por defecto
$Token = $env:SONAR_TOKEN
if (-not $Token -and (Test-Path $TokenFile)) { $Token = (Get-Content $TokenFile -Raw).Trim() }
if (-not $Token) {
    # No se usan credenciales por defecto (admin:admin): el token se crea a mano una sola vez.
    Write-Host @"
No hay token de SonarQube. Créalo una sola vez:
  1. Abre $SonarUrl e inicia sesión como admin (te pedirá cambiar la contraseña por defecto).
  2. My Account > Security > Generate Token (tipo: Global Analysis Token).
  3. Guárdalo en sonar/.sonar-token (ignorado por git) o en la variable de entorno SONAR_TOKEN.
"@ -ForegroundColor Yellow
    exit 1
}

if (-not $SkipBuild) {
    foreach ($svc in $Services) {
        Write-Host "==> Compilando, probando y copiando dependencias: $svc" -ForegroundColor Cyan
        Push-Location (Join-Path $Root "Backend/$svc")
        try {
            # Las pruebas fallidas no detienen el análisis; quedan registradas en el reporte de Maven
            mvn -B -q test dependency:copy-dependencies "-DincludeScope=runtime" "-Dmaven.test.failure.ignore=true"
            if ($LASTEXITCODE -ne 0) { Write-Warning "Maven terminó con errores en $svc" }
        } finally { Pop-Location }
    }
}

Write-Host "==> Ejecutando SonarScanner" -ForegroundColor Cyan
docker run --rm `
    -e SONAR_HOST_URL="http://host.docker.internal:9000" `
    -e SONAR_TOKEN=$Token `
    -v "${Root}:/usr/src" `
    sonarsource/sonar-scanner-cli
if ($LASTEXITCODE -ne 0) { throw "El análisis de SonarScanner falló" }

Write-Host "==> Listo. Resultados en $SonarUrl/dashboard?id=pedidos360" -ForegroundColor Green
