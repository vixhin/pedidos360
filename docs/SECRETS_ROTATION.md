# Procedimiento de Rotación de Secretos — Pedidos360

Este procedimiento describe el paso a paso para renovar de manera segura las credenciales y claves criptográficas de la plataforma sin generar tiempos de inactividad prolongados ni brechas de seguridad.

---

## 1. Rotación de `JWT_SECRET` (Clave de Firma de Sesión)

La clave `JWT_SECRET` es utilizada por `usuario-service` para firmar tokens y por el resto de los microservicios para validarlos.

### Procedimiento:
1. **Generar un nuevo secreto de al menos 32 caracteres:**
   ```bash
   openssl rand -hex 32
   ```
2. **Actualizar `.env.production` en el servidor EC2:**
   ```bash
   nano /opt/pedidos360/Backend/.env.production
   # Actualizar JWT_SECRET=<NUEVO_SECRETO>
   ```
3. **Reiniciar los servicios de backend simultáneamente:**
   ```bash
   cd /opt/pedidos360/Backend
   docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d usuario pedidos carrito bff analitica notificacion chat
   ```
4. **Efecto operativo:**
   - Las sesiones activas requerirán refresco de token o inicio de sesión nuevamente.
   - Las sesiones revocadas en `user_sessions` siguen siendo inválidas.

---

## 2. Rotación de `BFF_INTERNAL_KEY` (Clave S2S de Microservicios)

La clave `BFF_INTERNAL_KEY` autentica las peticiones que viajan internamente desde el BFF hacia los microservicios protegidos.

### Procedimiento:
1. **Generar una nueva clave secreta:**
   ```bash
   openssl rand -hex 32
   ```
2. **Actualizar `.env.production` en el servidor EC2:**
   ```bash
   nano /opt/pedidos360/Backend/.env.production
   # Actualizar BFF_INTERNAL_KEY=<NUEVA_CLAVE>
   ```
3. **Reiniciar todos los contenedores de backend:**
   ```bash
   cd /opt/pedidos360/Backend
   docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d
   ```
4. **Validación:**
   - Realice una petición autenticada desde el frontend o mediante `curl` hacia el BFF para confirmar que no se generen errores 403 Forbidden S2S.

---

## 3. Rotación de Contraseña de Base de Datos PostgreSQL

### Procedimiento:
1. **Acceder a PostgreSQL desde la consola:**
   ```bash
   docker exec -it pedidos360-postgres psql -U postgres
   ```
2. **Ejecutar cambio de contraseña SQL:**
   ```sql
   ALTER USER pedidos360_admin WITH PASSWORD 'NUEVO_PASSWORD_COMPLEJO_2026!';
   \q
   ```
3. **Actualizar `.env.production` con el nuevo password:**
   ```bash
   nano /opt/pedidos360/Backend/.env.production
   # POSTGRES_PASSWORD=NUEVO_PASSWORD_COMPLEJO_2026!
   # DB_PASSWORD=NUEVO_PASSWORD_COMPLEJO_2026!
   ```
4. **Reiniciar los microservicios que conectan a la base de datos:**
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d
   ```

---

## 4. Rotación de Claves Cloudflare Turnstile

### Procedimiento:
1. Generar nuevo widget y claves en el Dashboard de Cloudflare.
2. Actualizar `TURNSTILE_SITE_KEY` y `TURNSTILE_SECRET_KEY` en `.env.production`.
3. Reconstruir el frontend para inyectar la nueva clave pública:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d --build frontend usuario
   ```

