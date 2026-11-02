# Procedimiento Seguro de Bootstrap Administrativo — Pedidos360

El endpoint `POST /api/auth/bootstrap-admin` permite inicializar el **único** administrador inicial del sistema durante el aprovisionamiento.

## Reglas de Seguridad

1. **Deshabilitado por defecto**: La variable de entorno `ADMIN_BOOTSTRAP_ENABLED` debe ser explícitamente `true` para que el endpoint responda; de lo contrario retorna `404 Not Found`.
2. **Token criptográfico obligatorio**: La variable `ADMIN_BOOTSTRAP_TOKEN` debe estar configurada en el entorno con un valor de al menos 32 caracteres (generado con `openssl rand -base64 32`).
3. **Un solo administrador**: Si la base de datos ya contiene algún usuario con rol `ADMIN`, el endpoint responderá `409 Conflict` ("El sistema ya cuenta con un administrador registrado. Bootstrap bloqueado.") impidiendo la creación de administradores adicionales.
4. **Política de contraseña**: La contraseña debe contener al menos 12 caracteres.

## Procedimiento de Inicialización

1. Generar token de bootstrap seguro:
   ```bash
   BOOTSTRAP_TOKEN=$(openssl rand -base64 32)
   echo "Token temporal: $BOOTSTRAP_TOKEN"
   ```

2. Configurar temporalmente en el archivo `.env`:
   ```bash
   ADMIN_BOOTSTRAP_ENABLED=true
   ADMIN_BOOTSTRAP_TOKEN=<token_generado>
   ```

3. Reiniciar el servicio `usuario-service`:
   ```bash
   docker compose restart usuario
   ```

4. Ejecutar la llamada de aprovisionamiento vía cURL:
   ```bash
   curl -X POST https://<tu-dominio>/api/auth/bootstrap-admin \
     -H "Content-Type: application/json" \
     -d '{
       "email": "admin@pedidos360.cl",
       "nombre": "Administrador General",
       "password": "TuPasswordSuperSegura2026!",
       "telefono": "+56912345678",
       "bootstrapToken": "<token_generado>"
     }'
   ```

5. **Inmediatamente después de recibir HTTP 201 Created**:
   - Cambiar en el archivo `.env`:
     ```bash
     ADMIN_BOOTSTRAP_ENABLED=false
     unset ADMIN_BOOTSTRAP_TOKEN
     ```
   - Reiniciar el contenedor `usuario` para desactivar el endpoint permanentemente.

