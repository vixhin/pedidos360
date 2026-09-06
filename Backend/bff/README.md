# BFF — Backend for Frontend (Pedidos360)

## ¿Qué es el BFF?

El BFF actúa como puerta de entrada entre el frontend Angular y los microservicios internos.

**Flujo completo:**
```
Angular (localhost:4200)
  │  Authorization: Bearer <ACCESS_TOKEN de Microsoft Entra ID>
  ▼
BFF (localhost:8090)
  │  1. Valida firma RSA (JWKS de Microsoft, automático)
  │  2. Valida issuer (https://login.microsoftonline.com/{tenant}/v2.0)
  │  3. Valida audience (api://{apiClientId})
  │  4. Valida expiración (claim exp)
  │  5. Valida scope (SCOPE_access_as_user)
  │  6. Valida roles (ROLE_ADMIN, ROLE_VENDEDOR, ROLE_CLIENTE)
  │
  ├─▶ usuario-service   :8081
  ├─▶ pedidos-service   :8082
  ├─▶ carrito-service   :8083
  ├─▶ analitica-service :8084
  ├─▶ productos-service :8085
  └─▶ notificacion-service :8086
         │
         ▼
     PostgreSQL
```

---

## Configuración de Microsoft Entra ID

### Paso 1: App Registration SPA (Frontend Angular)

1. Ir a [portal.azure.com](https://portal.azure.com) → **Microsoft Entra ID** → **Registros de aplicaciones** → **+ Nuevo registro**
2. Nombre: `pedidos360-frontend`
3. Tipo de cuenta: Cuentas en este directorio
4. Plataforma: **SPA** (Single-page application)
5. URI de redirección: `http://localhost:4200`
6. Copiar:
   - **Application (client) ID** → `AZURE_SPA_CLIENT_ID` (para el frontend)
   - **Directory (tenant) ID** → `AZURE_TENANT_ID`

### Paso 2: App Registration API/BFF

1. **+ Nuevo registro** → Nombre: `pedidos360-bff`
2. Ir a **Exponer una API** → **+ Agregar ámbito**:
   - URI de ID de aplicación: se genera automáticamente (`api://<clientId>`)
   - Nombre del ámbito: `access_as_user`
   - ¿Quién puede dar consentimiento?: Administradores y usuarios
3. Copiar:
   - **Application (client) ID** → `AZURE_API_CLIENT_ID`

### Paso 3: Roles de aplicación (App Registration BFF)

En el App Registration del BFF → **Roles de aplicación** → **+ Crear rol de aplicación**:
| Nombre | Valor | Descripción |
|--------|-------|-------------|
| ADMIN | `ADMIN` | Administrador del sistema |
| VENDEDOR | `VENDEDOR` | Vendedor / gestión de inventario |
| CLIENTE | `CLIENTE` | Cliente comprador |

### Paso 4: Permisos del frontend

En el App Registration SPA → **Permisos de API** → **+ Agregar permiso**:
- Seleccionar `pedidos360-bff` → ámbito `access_as_user`

### Paso 5: Asignar roles a usuarios

**Microsoft Entra ID** → **Aplicaciones empresariales** → `pedidos360-bff` → **Usuarios y grupos** → **+ Agregar usuario/grupo** → seleccionar usuario y asignar rol.

---

## Variables de entorno

### BFF (application.properties / Docker)
| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `AZURE_TENANT_ID` | Directory (tenant) ID | `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` |
| `AZURE_API_CLIENT_ID` | Application ID del BFF | `yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy` |
| `FRONTEND_ORIGIN` | URL del frontend Angular | `http://localhost:4200` |
| `USUARIO_SERVICE_URL` | URL del usuario-service | `http://localhost:8081` |
| `PEDIDOS_SERVICE_URL` | URL del pedidos-service | `http://localhost:8082` |
| `CARRITO_SERVICE_URL` | URL del carrito-service | `http://localhost:8083` |
| `ANALITICA_SERVICE_URL` | URL del analitica-service | `http://localhost:8084` |
| `PRODUCTOS_SERVICE_URL` | URL del productos-service | `http://localhost:8085` |
| `NOTIFICACION_SERVICE_URL` | URL del notificacion-service | `http://localhost:8086` |

### Frontend Angular (environment.ts)
| Campo | Descripción |
|-------|-------------|
| `azure.clientId` | Application ID del App Registration SPA |
| `azure.tenantId` | Directory (tenant) ID |
| `azure.apiClientId` | Application ID del BFF |

---

## Validaciones JWT

| Claim | Validación | Responsable |
|-------|-----------|-------------|
| `sig` | Firma RSA via JWKS de Microsoft | Spring Security (automático) |
| `iss` | `https://login.microsoftonline.com/{tenant}/v2.0` | Spring Security (automático) |
| `exp` | Token no expirado | Spring Security (automático) |
| `nbf` | Token no antes-de | Spring Security (automático) |
| `aud` | `api://{apiClientId}` | `AudienceValidator.java` (custom) |
| `scp` | `access_as_user` | Spring Security authorities |
| `roles` | `ADMIN`, `VENDEDOR`, `CLIENTE` | `AzureJwtAuthConverter.java` |

---

## Autorización por endpoint

| Endpoint | Método | Rol/Scope requerido |
|----------|--------|---------------------|
| `/api/bff/productos` | GET | `SCOPE_access_as_user` |
| `/api/bff/productos` | POST | `ROLE_VENDEDOR` o `ROLE_ADMIN` |
| `/api/bff/productos/**` | PUT | `ROLE_VENDEDOR` o `ROLE_ADMIN` |
| `/api/bff/productos/**` | DELETE | `ROLE_ADMIN` |
| `/api/bff/pedidos/**` | GET, POST | `SCOPE_access_as_user` |
| `/api/bff/pedidos/**` | PUT, DELETE | `ROLE_ADMIN` |
| `/api/bff/carrito/**` | * | `SCOPE_access_as_user` |
| `/api/bff/notificaciones/**` | * | `SCOPE_access_as_user` |
| `/api/bff/usuarios/me` | GET | `SCOPE_access_as_user` |
| `/api/bff/usuarios/**` | * | `ROLE_ADMIN` |
| `/api/bff/analitica/**` | * | `ROLE_ADMIN` |
| `/health` | GET | Público |
| `/ready` | GET | Público |

---

## Respuestas de error

### 401 Unauthorized (token ausente, inválido o expirado)
```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Token inválido, expirado o ausente",
  "timestamp": "2026-09-05T21:00:00Z"
}
```

### 403 Forbidden (token válido pero sin permisos)
```json
{
  "status": 403,
  "error": "FORBIDDEN",
  "message": "No posee permisos suficientes",
  "timestamp": "2026-09-05T21:00:00Z"
}
```

---

## Ejecutar en desarrollo local

```bash
# Prerequisito: Maven 3.9+ y Java 21
cd Backend/bff

# Configurar variables de entorno
export AZURE_TENANT_ID=tu-tenant-id
export AZURE_API_CLIENT_ID=tu-api-client-id

# Ejecutar
mvn spring-boot:run
```

El BFF estará disponible en `http://localhost:8090`.

**Verificar salud:**
```bash
curl http://localhost:8090/health
# {"status":"UP","service":"bff","timestamp":"..."}
```

---

## Ejecutar con Docker Compose

```bash
# Crear .env en Backend/
echo "AZURE_TENANT_ID=tu-tenant-id" > Backend/.env
echo "AZURE_API_CLIENT_ID=tu-api-client-id" >> Backend/.env

cd Backend
docker compose up --build bff
```

---

## Ejecutar tests

```bash
cd Backend/bff
mvn test
```

Los tests NO requieren conexión a Azure. Usan `spring-security-test` con tokens mock.

---

## Logs del BFF

| Prefijo | Significado |
|---------|-------------|
| `[BFF][AUTH]` | Token validado / 401 |
| `[BFF][FORBIDDEN]` | Autorización denegada / 403 |
| `[BFF][PROXY]` | Llamada a microservicio iniciada |
| `[BFF][DOWNSTREAM]` | Respuesta del microservicio |

**El BFF NUNCA imprime el token en los logs.**
