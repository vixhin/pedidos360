# Script para levantar todos los microservicios de Pedidos360
# Uso por defecto: .\run-all.ps1
# Uso con PostgreSQL: .\run-all.ps1 -Profile postgres

param (
    [string]$Profile = ""
)

$services = @(
    @{ name="usuario"; port=8081 },
    @{ name="pedidos"; port=8082 },
    @{ name="carrito"; port=8083 },
    @{ name="analitica"; port=8084 },
    @{ name="productos"; port=8085 },
    @{ name="notificacion"; port=8086 }
)

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " Iniciando los 6 Microservicios Backend " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

foreach ($s in $services) {
    $name = $s.name
    $port = $s.port
    $dir = "c:\Users\marti\coding\pedidos360\Backend\$name"
    
    $args = "spring-boot:run"
    if ($Profile -ne "") {
        $args += " -Dspring-boot.run.profiles=$Profile"
    }

    Write-Host "Iniciando $name en puerto $port..." -ForegroundColor Yellow
    Start-Process -FilePath "$dir\mvnw.cmd" -ArgumentList $args -WorkingDirectory $dir -WindowStyle Minimized
}

Write-Host "Todos los microservicios han sido lanzados en procesos independientes." -ForegroundColor Green
Write-Host "Puertos activos:" -ForegroundColor Green
Write-Host "  - usuario:      http://localhost:8081/api/usuario/health"
Write-Host "  - pedidos:      http://localhost:8082/api/pedidos/health"
Write-Host "  - carrito:      http://localhost:8083/api/carrito/health"
Write-Host "  - analitica:    http://localhost:8084/api/analitica/health"
Write-Host "  - productos:    http://localhost:8085/api/productos/health"
Write-Host "  - notificacion: http://localhost:8086/api/notificacion/health"
