#!/usr/bin/env bash
# ==============================================================================
# SCRIPT DE ADMINISTRACIÓN Y GESTIÓN DE KAFKA - PEDIDOS360
# ==============================================================================
# Cumple el Indicador 9 de la Evaluación Parcial N° 5 (DSY1107):
# "El microservicio administrador o scripts de infraestructura incluyen operaciones
#  para crear, actualizar o inspeccionar tópicos y grupos de consumidores"
# ==============================================================================

set -euo pipefail

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9094}"
CONTAINER_NAME="${KAFKA_CONTAINER:-pedidos360-kafka}"

function print_usage() {
    echo "Uso: $0 <comando> [argumentos]"
    echo ""
    echo "Comandos disponibles:"
    echo "  list-topics                         Listar todos los tópicos existentes"
    echo "  describe-topic <nombre>             Ver detalles, particiones y réplicas de un tópico"
    echo "  create-topic <nombre> [part] [rep]  Crear un nuevo tópico (default 3 particiones, rep 1)"
    echo "  alter-topic-retention <nombre> <ms> Modificar retención en milisegundos"
    echo "  delete-topic <nombre>               Eliminar un tópico"
    echo "  list-groups                         Listar todos los Consumer Groups activos"
    echo "  describe-group <groupId>            Inspeccionar miembros, particiones asignadas y lag"
    echo "  health                              Verificar versión y conectividad con brokers"
    echo ""
    exit 1
}

if [ $# -lt 1 ]; then
    print_usage
fi

COMMAND="$1"
shift || true

case "$COMMAND" in
    list-topics)
        echo "=== Tópicos en Kafka ($BOOTSTRAP_SERVER) ==="
        docker exec -it "$CONTAINER_NAME" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
        ;;

    describe-topic)
        if [ $# -lt 1 ]; then
            echo "Error: Debe especificar el nombre del tópico."
            exit 1
        fi
        TOPIC_NAME="$1"
        echo "=== Descripción del Tópico: $TOPIC_NAME ==="
        docker exec -it "$CONTAINER_NAME" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic "$TOPIC_NAME"
        ;;

    create-topic)
        if [ $# -lt 1 ]; then
            echo "Error: Debe especificar el nombre del tópico."
            exit 1
        fi
        TOPIC_NAME="$1"
        PARTITIONS="${2:-3}"
        REPLICATION="${3:-1}"
        echo "=== Creando Tópico: $TOPIC_NAME (Particiones: $PARTITIONS, Factor Replicación: $REPLICATION) ==="
        docker exec -it "$CONTAINER_NAME" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
            --create --if-not-exists --topic "$TOPIC_NAME" \
            --partitions "$PARTITIONS" --replication-factor "$REPLICATION" \
            --config retention.ms=86400000 --config cleanup.policy=delete
        echo "Tópico $TOPIC_NAME creado o verificado exitosamente."
        ;;

    alter-topic-retention)
        if [ $# -lt 2 ]; then
            echo "Error: Requiere <nombre-topico> y <retention-ms>."
            exit 1
        fi
        TOPIC_NAME="$1"
        RETENTION_MS="$2"
        echo "=== Alterando retención de $TOPIC_NAME a $RETENTION_MS ms ==="
        docker exec -it "$CONTAINER_NAME" /opt/kafka/bin/kafka-configs.sh --bootstrap-server localhost:9092 \
            --entity-type topics --entity-name "$TOPIC_NAME" --alter --add-config "retention.ms=$RETENTION_MS"
        ;;

    delete-topic)
        if [ $# -lt 1 ]; then
            echo "Error: Debe especificar el nombre del tópico."
            exit 1
        fi
        TOPIC_NAME="$1"
        echo "=== Eliminando Tópico: $TOPIC_NAME ==="
        docker exec -it "$CONTAINER_NAME" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic "$TOPIC_NAME"
        echo "Tópico $TOPIC_NAME eliminado."
        ;;

    list-groups)
        echo "=== Consumer Groups Activos ==="
        docker exec -it "$CONTAINER_NAME" /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
        ;;

    describe-group)
        if [ $# -lt 1 ]; then
            echo "Error: Debe especificar el groupId."
            exit 1
        fi
        GROUP_ID="$1"
        echo "=== Inspección y Lag del Consumer Group: $GROUP_ID ==="
        docker exec -it "$CONTAINER_NAME" /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group "$GROUP_ID"
        ;;

    health)
        echo "=== Verificación de Conectividad y Broker API Versions ==="
        docker exec -it "$CONTAINER_NAME" /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092
        ;;

    *)
        echo "Comando no reconocido: $COMMAND"
        print_usage
        ;;
esac

