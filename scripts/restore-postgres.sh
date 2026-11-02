#!/usr/bin/env bash
# ==============================================================================
# Script de Restauración de PostgreSQL — Pedidos360
# ==============================================================================
# Restaura el dump comprimido (.sql.gz) en el contenedor de PostgreSQL
# con verificación previa de integridad.
# ==============================================================================
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Uso: $0 <ruta_al_archivo_backup.sql.gz> [--force]"
  exit 1
fi

BACKUP_FILE="$1"
FORCE_RESTORE="${2:-}"
CONTAINER_NAME="${CONTAINER_NAME:-pedidos360-postgres}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "[ERROR] El archivo de respaldo no existe: ${BACKUP_FILE}" >&2
  exit 1
fi

echo "[INFO] Verificando integridad del archivo comprimido..."
if ! gzip -t "${BACKUP_FILE}"; then
  echo "[ERROR] El archivo de respaldo está dañado o no es un gzip válido." >&2
  exit 2
fi

if [ "${FORCE_RESTORE}" != "--force" ]; then
  read -rp "ADVERTENCIA: Esta operación sobreescribirá datos en ${CONTAINER_NAME}. ¿Desea continuar? (s/N): " CONFIRM
  if [[ ! "${CONFIRM}" =~ ^[sS]$ ]]; then
    echo "Operación cancelada por el usuario."
    exit 0
  fi
fi

echo "[INFO] Restaurando datos en el contenedor ${CONTAINER_NAME}..."
gunzip -c "${BACKUP_FILE}" | docker exec -i "${CONTAINER_NAME}" psql -U "${POSTGRES_USER}" -d postgres

echo "[INFO] Validando bases de datos restauradas..."
docker exec -i "${CONTAINER_NAME}" psql -U "${POSTGRES_USER}" -d postgres -c "\l"

echo "[SUCCESS] Restauración de PostgreSQL finalizada con éxito."

