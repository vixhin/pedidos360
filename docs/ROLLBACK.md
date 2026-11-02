# Procedimiento de Rollback de Emergencia — Pedidos360

Este documento describe las acciones operativas necesarias para revertir un despliegue fallido o restaurar la plataforma a un estado anterior estable sin pérdida de datos.

---

## 1. Rollback a Nivel de Código / Contenedores

Si un nuevo despliegue presenta errores en runtime o fallas críticas en los microservicios:

### Paso 1: Revertir Git al Commit Anterior Estable
En el host EC2 (`/opt/pedidos360`):
```bash
cd /opt/pedidos360
git status
# Identificar commit anterior
git log -n 5 --oneline
# Revertir el árbol de trabajo al commit estable
git checkout <COMMIT_SHA_ESTABLE>
```

### Paso 2: Reconstruir y Reiniciar los Contenedores
```bash
cd /opt/pedidos360/Backend
docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d --build
```

### Paso 3: Validar Estado de Salud
```bash
docker compose ps
docker compose logs --tail=50 bff
```

---

## 2. Rollback a Nivel de Base de Datos PostgreSQL

Si una migración Flyway o un script corrupto afectó el esquema o los datos en producción:

### Paso 1: Detener los Microservicios para Evitar Escrituras Concurrente
```bash
cd /opt/pedidos360/Backend
docker compose stop usuario pedidos carrito productos analitica notificacion chat bff
```

### Paso 2: Localizar el Último Respaldo Válido
```bash
ls -lth /opt/pedidos360/backups/
```
Identifique el archivo más reciente (ej: `pedidos360_db_20261101_180000.sql.gz`).

### Paso 3: Ejecutar el Script de Restauración
```bash
/opt/pedidos360/scripts/restore-postgres.sh /opt/pedidos360/backups/pedidos360_db_20261101_180000.sql.gz
```
El script verificará el hash y la integridad gzip del archivo antes de inyectar las sentencias en PostgreSQL.

### Paso 4: Reiniciar los Microservicios
```bash
docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d
```

### Paso 5: Verificar Validación de Esquema Hibernate
```bash
docker compose logs pedidos | grep -E "Hibernate|Flyway"
```
Los microservicios deben arrancar con `ddl-auto=validate` confirmando que el esquema coincide con el esperado.

