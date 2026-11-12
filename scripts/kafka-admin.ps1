<#
.SYNOPSIS
    Script PowerShell para administración y gestión de Apache Kafka - Pedidos360.
.DESCRIPTION
    Cumple el Indicador 9 de la Evaluación Parcial N° 5 (DSY1107):
    "El microservicio administrador o scripts de infraestructura incluyen operaciones
     para crear, actualizar o inspeccionar tópicos y grupos de consumidores"
#>

[CmdletBinding()]
param(
    [Parameter(Position=0, Mandatory=$true)]
    [ValidateSet("list-topics", "describe-topic", "create-topic", "alter-topic-retention", "delete-topic", "list-groups", "describe-group", "health")]
    [string]$Command,

    [Parameter(Position=1)]
    [string]$Target,

    [Parameter(Position=2)]
    [int]$Partitions = 3,

    [Parameter(Position=3)]
    [int]$ReplicationFactor = 1
)

$containerName = if ($env:KAFKA_CONTAINER) { $env:KAFKA_CONTAINER } else { "pedidos360-kafka" }

switch ($Command) {
    "list-topics" {
        Write-Host "=== Listando Tópicos de Kafka ===" -ForegroundColor Cyan
        docker exec -it $containerName /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
    }

    "describe-topic" {
        if (-not $Target) {
            Write-Error "Debe especificar el nombre del tópico. Ej: .\kafka-admin.ps1 describe-topic pedidos360.pedidos.v1"
            return
        }
        Write-Host "=== Detalle de Tópico: $Target ===" -ForegroundColor Cyan
        docker exec -it $containerName /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic $Target
    }

    "create-topic" {
        if (-not $Target) {
            Write-Error "Debe especificar el nombre del tópico. Ej: .\kafka-admin.ps1 create-topic nuevo.topico 3 1"
            return
        }
        Write-Host "=== Creando Tópico: $Target (Particiones: $Partitions, Replicas: $ReplicationFactor) ===" -ForegroundColor Cyan
        docker exec -it $containerName /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 `
            --create --if-not-exists --topic $Target `
            --partitions $Partitions --replication-factor $ReplicationFactor `
            --config retention.ms=86400000 --config cleanup.policy=delete
    }

    "alter-topic-retention" {
        if (-not $Target -or -not $Partitions) {
            Write-Error "Requiere <nombre-topico> y <retention-ms>. Ej: .\kafka-admin.ps1 alter-topic-retention pedidos360.pedidos.v1 86400000"
            return
        }
        Write-Host "=== Alterando retención de $Target a $Partitions ms ===" -ForegroundColor Cyan
        docker exec -it $containerName /opt/kafka/bin/kafka-configs.sh --bootstrap-server localhost:9092 `
            --entity-type topics --entity-name $Target --alter --add-config "retention.ms=$Partitions"
    }

    "delete-topic" {
        if (-not $Target) {
            Write-Error "Debe especificar el nombre del tópico a eliminar."
            return
        }
        Write-Host "=== Eliminando Tópico: $Target ===" -ForegroundColor Yellow
        docker exec -it $containerName /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic $Target
    }

    "list-groups" {
        Write-Host "=== Listando Consumer Groups Activos ===" -ForegroundColor Cyan
        docker exec -it $containerName /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
    }

    "describe-group" {
        if (-not $Target) {
            Write-Error "Debe especificar el groupId. Ej: .\kafka-admin.ps1 describe-group analitica-group"
            return
        }
        Write-Host "=== Inspeccionando Consumer Group: $Target ===" -ForegroundColor Cyan
        docker exec -it $containerName /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group $Target
    }

    "health" {
        Write-Host "=== Verificando Estado y Broker API Versions ===" -ForegroundColor Cyan
        docker exec -it $containerName /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092
    }
}

