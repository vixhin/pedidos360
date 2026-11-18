#!/usr/bin/env bash
# ==============================================================================
# Pedidos360 — Inicializador Idempotente de Bases de Datos en AWS RDS PostgreSQL
# ==============================================================================
# Este script crea las 7 bases de datos necesarias para los microservicios
# en la instancia de AWS RDS provisionada por Terraform.
#
# Uso:
#   DB_HOST="pedidos360-db-prod.xxxx.us-east-1.rds.amazonaws.com" \
#   DB_USER="postgres" \
#   DB_PASSWORD="Pedidos360Secure2026!" \
#   ./scripts/init-rds-dbs.sh
#
# O si existe Backend/.env.production o Backend/.env, se cargan automáticamente:
#   ./scripts/init-rds-dbs.sh
# ==============================================================================

set -euo pipefail

# Cargar variables de entorno si existen archivos .env
if [ -f "Backend/.env.production" ]; then
    echo "[INFO] Cargando variables desde Backend/.env.production..."
    # shellcheck disable=SC1091
    set -a; source Backend/.env.production; set +a
elif [ -f "Backend/.env" ]; then
    echo "[INFO] Cargando variables desde Backend/.env..."
    # shellcheck disable=SC1091
    set -a; source Backend/.env; set +a
fi

DB_HOST="${DB_HOST:-${1:-}}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-${POSTGRES_USER:-postgres}}"
DB_PASSWORD="${DB_PASSWORD:-${POSTGRES_PASSWORD:-}}"

if [ -z "$DB_HOST" ]; then
    echo "[ERROR] DB_HOST no está definido. Proporciónelo como variable de entorno o argumento."
    echo "Ejemplo: DB_HOST=pedidos360-db-prod.xxxx.us-east-1.rds.amazonaws.com DB_PASSWORD=xxxx ./scripts/init-rds-dbs.sh"
    exit 1
fi

if [ -z "$DB_PASSWORD" ]; then
    echo -n "Ingrese contraseña para PostgreSQL ($DB_USER@$DB_HOST): "
    read -rs DB_PASSWORD
    echo ""
fi

DATABASES=(
    "usuario_db"
    "pedidos_db"
    "carrito_db"
    "analitica_db"
    "productos_db"
    "notificacion_db"
    "chat_db"
)

echo "=================================================================="
echo " Conectando a AWS RDS PostgreSQL: $DB_HOST:$DB_PORT"
echo " Usuario: $DB_USER"
echo "=================================================================="

# Función para ejecutar consultas SQL con psql nativo o contenedor docker efímero
run_sql() {
    local sql_query="$1"
    if command -v psql >/dev/null 2>&1; then
        PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -tAc "$sql_query"
    elif command -v docker >/dev/null 2>&1; then
        docker run --rm --net=host \
            -e PGPASSWORD="$DB_PASSWORD" \
            postgres:16-alpine \
            psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -tAc "$sql_query"
    else
        echo "[ERROR] No se encontró 'psql' ni 'docker' en el sistema."
        exit 1
    fi
}

echo "[INFO] Verificando y creando bases de datos de microservicios..."
for db in "${DATABASES[@]}"; do
    EXISTS=$(run_sql "SELECT 1 FROM pg_database WHERE datname = '$db';" || true)
    if [ "$EXISTS" = "1" ]; then
        echo "  [OK] Base de datos '$db' ya existe."
    else
        echo "  [CREANDO] Creando base de datos '$db'..."
        run_sql "CREATE DATABASE $db;"
        echo "  [CREADA] '$db' creada exitosamente."
    fi
done

echo "=================================================================="
echo " [ÉXITO] Todas las bases de datos de microservicios están listas"
echo " en la instancia AWS RDS PostgreSQL."
echo "=================================================================="

