#!/usr/bin/env bash
# ==============================================================================
# Script de Respaldo Automatizado de PostgreSQL — Pedidos360
# ==============================================================================
# Realiza backup de todas las bases de datos de microservicios con pg_dumpall,
# compresión gzip y verificación de integridad.
# ==============================================================================
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/pedidos360/backups}"
CONTAINER_NAME="${CONTAINER_NAME:-pedidos360-postgres}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/pedidos360_db_${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

echo "[INFO] Iniciando respaldo de PostgreSQL: $(date)"
echo "[INFO] Contenedor: ${CONTAINER_NAME}, Usuario: ${POSTGRES_USER}"

# Verificar que el contenedor esté corriendo
if ! docker ps --filter "name=^/${CONTAINER_NAME}$" --format '{{.Names}}' | grep -q "${CONTAINER_NAME}"; then
  echo "[ERROR] El contenedor ${CONTAINER_NAME} no está en ejecución." >&2
  exit 1
fi

# Ejecutar pg_dumpall y comprimir en streaming
docker exec -t "${CONTAINER_NAME}" pg_dumpall -U "${POSTGRES_USER}" --clean --if-exists | gzip -9 > "${BACKUP_FILE}"

# Verificar integridad del archivo generado
if ! gzip -t "${BACKUP_FILE}"; then
  echo "[ERROR] El archivo de respaldo está corrupto." >&2
  rm -f "${BACKUP_FILE}"
  exit 2
fi

FILE_SIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
echo "[SUCCESS] Respaldo completado exitosamente: ${BACKUP_FILE} (${FILE_SIZE})"

# Subida opcional a S3 (si BACKUP_S3_BUCKET está definido)
if [ -n "${BACKUP_S3_BUCKET:-}" ]; then
  echo "[INFO] Subiendo respaldo a S3: s3://${BACKUP_S3_BUCKET}/backups/"
  aws s3 cp "${BACKUP_FILE}" "s3://${BACKUP_S3_BUCKET}/backups/$(basename "${BACKUP_FILE}")"
  echo "[SUCCESS] Respaldo subido a S3."
fi

# Rotación de respaldos antiguos (> RETENTION_DAYS días)
echo "[INFO] Purgando respaldos locales mayores a ${RETENTION_DAYS} días..."
find "${BACKUP_DIR}" -name "pedidos360_db_*.sql.gz" -mtime +"${RETENTION_DAYS}" -exec rm -vf {} \;
echo "[SUCCESS] Proceso de respaldo finalizado."

