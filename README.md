# Pedidos360 — Sistema de Gestión de Pedidos

Plataforma de e-commerce para gestión de pedidos, productos, carrito y analítica, con autenticación mediante **Microsoft Entra ID (Azure AD)** y arquitectura **BFF (Backend for Frontend)**.

---

## Arquitectura

```
Angular (localhost:4200)
  │  Authorization: Bearer <Access Token de Microsoft Entra ID>
  ▼
BFF — Backend for Frontend (localhost:8090)
  │  Valida JWT: firma, issuer, audience, expiration, scopes, roles
  │
  ├─▶ usuario-service   (localhost:8081) — PostgreSQL: usuario_db
  ├─▶ pedidos-service   (localhost:8082) — PostgreSQL: pedidos_db
  ├─▶ carrito-service   (localhost:8083) — PostgreSQL: carrito_db
  ├─▶ analitica-service (localhost:8084) — PostgreSQL: analitica_db
  ├─▶ productos-service (localhost:8085) — PostgreSQL: productos_db
  └─▶ notificacion-service (localhost:8086) — PostgreSQL: notificacion_db
```

**Compatibilidad:** El login local (email/contraseña) con JWT interno sigue funcionando en paralelo al login con Microsoft.

---

## Autenticación

### Microsoft Entra ID (MSAL)
- Flujo: **Authorization Code + PKCE** (SPA)
- Librería Angular: `@azure/msal-angular` v4
- Los roles del usuario provienen del claim `roles` del Access Token
- El Access Token se adjunta automáticamente via `MsalInterceptor` al BFF

### Login Local (Base de datos)
- Email/contraseña contra `usuario-service`
- JWT interno generado por el servicio con JJWT
- Roles provienen de la base de datos

### Credenciales de prueba (login local)
| Usuario | Contraseña | Rol |
|---------|-----------|-----|
| admin@pedidos360.cl | chupalovixo | ADMIN |
| vendedor@pedidos360.cl | chupalovixo | VENDEDOR |
| cliente@pedidos360.cl | chupalovixo | CLIENTE |

---

## Configuración de Azure Entra ID

Ver: [Backend/bff/README.md](Backend/bff/README.md)

**Resumen:**
1. Crear App Registration SPA (frontend) → copiar `clientId` y `tenantId`
2. Crear App Registration API (BFF) → exponer scope `access_as_user`
3. Configurar roles: `ADMIN`, `VENDEDOR`, `CLIENTE`
4. Editar `pedidos360-frontend/src/environments/environment.ts` con los IDs

---

## Estructura del proyecto

```
pedidos360/
├── pedidos360-frontend/          # Angular 21 + MSAL
│   └── src/
│       ├── environments/
│       │   ├── environment.ts    # ← Configurar azure.clientId, tenantId, apiClientId
│       │   └── environment.prod.ts
│       └── app/
│           └── core/
│               ├── config/
│               │   ├── auth.config.ts     # Lee de environments
│               │   └── api.config.ts      # URLs BFF + microservicios
│               ├── guards/
│               │   └── role.guard.ts      # Guard de roles
│               └── services/
│                   ├── auth.service.ts    # Login Microsoft + local
│                   └── token-claims.service.ts  # Claims del JWT
└── Backend/
    ├── bff/                      # BFF Spring Boot (puerto 8090)
    │   └── README.md             # ← Ver este archivo para setup Azure
    ├── usuario/                  # puerto 8081
    ├── pedidos/                  # puerto 8082
    ├── carrito/                  # puerto 8083
    ├── analitica/                # puerto 8084
    ├── productos/                # puerto 8085
    ├── notificacion/             # puerto 8086
    └── docker-compose.yml
```

---

## Inicio rápido

### Opción A: Desarrollo local (sin Docker)

```bash
# 1. Bases de datos (PostgreSQL via Docker)
cd Backend
docker compose up postgres pgadmin -d

# 2. Microservicios (en terminales separadas)
cd Backend/usuario    && mvn spring-boot:run
cd Backend/productos  && mvn spring-boot:run
cd Backend/pedidos    && mvn spring-boot:run
cd Backend/carrito    && mvn spring-boot:run
cd Backend/analitica  && mvn spring-boot:run
cd Backend/notificacion && mvn spring-boot:run

# 3. BFF (con credenciales Azure)
cd Backend/bff
$env:AZURE_TENANT_ID="tu-tenant-id"
$env:AZURE_API_CLIENT_ID="tu-api-client-id"
mvn spring-boot:run

# 4. Frontend Angular
cd pedidos360-frontend
npm install
npm start    # http://localhost:4200
```

### Opción B: Docker Compose completo

```bash
# Crear archivo .env con credenciales Azure
cd Backend
echo "AZURE_TENANT_ID=tu-tenant-id" > .env
echo "AZURE_API_CLIENT_ID=tu-api-client-id" >> .env

docker compose up --build
```

---

## Puertos

| Servicio | Puerto | Descripción |
|---------|--------|-------------|
| Angular | 4200 | Frontend |
| BFF | 8090 | Proxy con JWT de Azure |
| usuario-service | 8081 | Gestión de usuarios + auth local |
| pedidos-service | 8082 | Pedidos |
| carrito-service | 8083 | Carrito de compras |
| analitica-service | 8084 | Analítica y reportes |
| productos-service | 8085 | Catálogo de productos |
| notificacion-service | 8086 | Notificaciones |
| PostgreSQL | 5432 | Base de datos |
| pgAdmin | 5050 | Gestión visual de BD |

---

## Flujo end-to-end con Microsoft Entra ID

```
1. Usuario abre Angular → click "Iniciar sesión con Microsoft"
2. MSAL redirige a login.microsoftonline.com
3. Microsoft Entra autentica al usuario
4. Microsoft devuelve ID Token + Access Token
5. MSAL almacena los tokens en localStorage
6. AuthService.syncFromMsal() lee roles del claim "roles"
7. MsalInterceptor adjunta automáticamente:
   Authorization: Bearer <ACCESS_TOKEN>
   en todas las llamadas al BFF (http://localhost:8090/api/bff/*)
8. BFF recibe la request
9. Spring Security valida la firma RSA (JWKS de Microsoft)
10. Spring Security valida el issuer
11. AudienceValidator valida el claim "aud"
12. Spring Security valida el exp
13. AzureJwtAuthConverter convierte roles → ROLE_XXX y scp → SCOPE_XXX
14. SecurityConfig aplica autorización según endpoint
15. BFF llama al microservicio downstream via WebClient
16. Microservicio consulta PostgreSQL
17. Respuesta regresa: microservicio → BFF → Angular
```
