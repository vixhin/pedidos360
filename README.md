# Pedidos360 — Sistema de Gestión de Pedidos & Repartidor con Chat en Tiempo Real

Plataforma e-commerce y logística en tiempo real para gestión de pedidos, productos, carrito, analítica, **portal de repartidor** y **chat en tiempo real (WebSocket/STOMP)**, con autenticación híbrida (**Microsoft Entra ID / Azure AD** y **JWT con cuenta local en BD**), arquitectura **BFF (Backend for Frontend)** sobre microservicios **Spring Boot 4 / Java 21**, eventos asíncronos con **RabbitMQ**, **PostgreSQL 16** y despliegue en **AWS EC2** con **Docker Compose**, **Caddy** (HTTPS automático) e infraestructura como código con **Terraform**.

**Frontend:** Angular 21 + MSAL · **Backend:** 7 microservicios + BFF · **Broker:** RabbitMQ 3.13 · **BD:** PostgreSQL 16 (una base por servicio, migraciones con Flyway)

---

## 📑 Contenido

1. [Arquitectura general](#️-arquitectura-general-del-sistema)
2. [Estructura del repositorio](#-estructura-del-repositorio)
3. [Autenticación híbrida y enrutamiento](#-autenticación-híbrida--enrutamiento-de-tráfico)
4. [Componentes principales](#-funcionamiento-interno-por-componente)
5. [Eventos asíncronos (RabbitMQ)](#-eventos-asíncronos-rabbitmq)
6. [Contenedores y puertos](#-microservicios--contenedores-docker-compose)
7. [Roles y credenciales de demo](#-roles-del-sistema--permisos)
8. [Variables de entorno](#-variables-de-entorno)
9. [Migraciones de base de datos](#️-migraciones-de-base-de-datos-flyway)
10. [Ejecución local](#️-ejecución-local-rápida)
11. [Infraestructura como código (Terraform)](#️-infraestructura-como-código-terraform-en-aws)
12. [Despliegue en AWS EC2](#-despliegue-en-aws-ec2)
13. [Pruebas y calidad](#-pruebas-y-calidad)
14. [Solución de problemas](#-solución-de-problemas)
15. [CI/CD](#-integración-continua-github-actions)
16. [Seguridad: pendientes conocidos](#-seguridad-pendientes-conocidos)
17. [Mejoras pendientes](#️-mejoras-pendientes)

---

## 🏗️ Arquitectura General del Sistema

```
                               Terraform (IaC)
                                      │
           ┌──────────────────────────┼──────────────────────────┐
           │                          │                          │
           v                          v                          v
    AWS Networking & SG         API Gateway & EIP          EC2 Instance
  (VPC, Subnets, SG 80/443/22)  (ANY /api/bff/*)        (Docker Engine)

                                  INTERNET
                                     │
                        ┌────────────┴────────────┐
                        │                         │
                  Microsoft REST               WebSocket /
                   (Entra ID)                  login local
                        │                         │
                        v                         │
                 AWS API Gateway                  │
               (ANY /api/bff/*)                   │
                        │                         │
                        v                         v
                 Elastic IP / Caddy <─────────────┘
              (https://*.sslip.io)
                        │
               ┌────────┴─────────┐
               │                  │
          Angular/nginx          BFF :8090
                                  │
         ┌────────────────┬───────┼────────┬───────────────┬───────────────┐
         │                │       │        │               │               │
       usuario         pedidos  carrito productos      analitica    notificacion
       :8081            :8082    :8083    :8085          :8084          :8086
                          │
                        chat :8091 (/ws-chat)
                          │
         ┌────────────────┴───────────────┐
         │                                │
    PostgreSQL 16                      RabbitMQ
  (7 DBs Flyway)                    (Events & DLQ)
```

> Dentro de la EC2 **solo Caddy publica puertos al host (80/443)**: el overlay `docker-compose.ec2.yml` elimina la publicación de puertos del resto de los contenedores (`ports: !reset []`). Los servicios se hablan por la red interna de Docker usando el nombre del servicio (p. ej. `http://usuario:8081`).

---

## 📁 Estructura del Repositorio

```
pedidos360/
├── Backend/
│   ├── usuario/ pedidos/ carrito/ productos/     # Microservicios Spring Boot (cada uno con su Dockerfile y mvnw)
│   ├── analitica/ notificacion/ chat/ bff/       # (analítica, notificaciones, chat WebSocket y BFF)
│   ├── docker-compose.yml                        # Stack completo para desarrollo local
│   ├── docker-compose.ec2.yml                    # Overlay para EC2: Caddy + frontend, puertos privados, perfil prod
│   ├── Caddyfile                                 # Reverse proxy HTTPS (rutas hacia cada servicio)
│   ├── init-dbs.sql                              # Creación de las 7 bases de datos
│   ├── .env.example / env/{dev,prod}/            # Plantillas de variables de entorno por ambiente
│   └── README.md                                 # Guía de uso de la API por microservicio
├── pedidos360-frontend/                          # Angular 21 + MSAL (+ pruebas e2e con Playwright en e2e/)
├── terraform/                                    # IaC: módulos (network, security_group, ec2, elastic_ip, api_gateway) y entornos dev/prod
├── scripts/                                      # deploy-ec2.sh / deploy-ec2.ps1 (despliegue idempotente por SSH)
└── .github/workflows/                            # ci.yml (tests/build/validaciones) y cd.yml (terraform plan)
```

---

## 🔐 Autenticación Híbrida & Enrutamiento de Tráfico

Pedidos360 implementa un esquema de autenticación doble:

1. **Microsoft Entra ID (Azure AD):**
   - **Flujo:** Angular (MSAL, Authorization Code + PKCE) ──▶ `/api/bff/*` (vía API Gateway o Caddy) ──▶ BFF (`:8090`) ──▶ microservicios internos.
   - El BFF actúa como *OAuth2 Resource Server*: valida firma (JWKS de Microsoft), `issuer`, `audience` (`api://<apiClientId>`), expiración y `scope` (`access_as_user`) antes de reenviar a los microservicios.
   - Tras el login, el frontend llama a `POST /api/bff/auth/entra-sync`: el BFF aprovisiona (JIT) o actualiza el usuario en la BD local y devuelve su ID numérico (los microservicios identifican usuarios por ese ID, no por el GUID de Azure).
2. **Login local (JWT propio, cuenta en BD):**
   - **Endpoints:** `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/forgot-password` (servicio `usuario`).
   - **Flujo:** Angular ──▶ Caddy (HTTPS) ──▶ microservicios directos. El `authInterceptor` adjunta `Authorization: Bearer <token_local>` solo cuando la sesión proviene del proveedor local, sin interferir con `MsalInterceptor`.

### Enrutamiento de Caddy (`Backend/Caddyfile`)

| Ruta | Destino |
| :--- | :--- |
| `/api/bff/*` | `bff:8090` |
| `/api/auth/*`, `/api/usuario/*` | `usuario:8081` |
| `/api/productos/*` | `productos:8085` |
| `/api/pedidos/*` | `pedidos:8082` |
| `/api/carrito/*` | `carrito:8083` |
| `/api/notificacion/*` | `notificacion:8086` |
| `/api/analitica/*` | `analitica:8084` |
| `/api/chat/*`, `/ws-chat*` | `chat:8091` |
| cualquier otra | `frontend:80` (Angular servido por nginx) |

---

## 💻 Funcionamiento Interno por Componente

### 1. Frontend Angular (`pedidos360-frontend`)
- **`core/interceptors/auth.interceptor.ts`:** si el proveedor activo es `db` (login local), agrega `Authorization: Bearer <token_local>` a las llamadas directas a microservicios.
- **`core/services/chat.service.ts` + `shared/components/chat/`:** conexión STOMP (`@stomp/stompjs`) al endpoint `/ws-chat`; envía el JWT en el frame `CONNECT` y se suscribe a `/topic/chat/{conversacionId}`. El **envío** de mensajes se hace por REST (`POST /api/chat/mensaje`); STOMP se usa para recibir en tiempo real.
- **`pages/courier/`:** portal de repartidor (pedidos disponibles, tomar pedido, cambiar estado, chat con el cliente).
- **`environments/`:** `environment.ts` (dev, apunta a `localhost`) y `environment.prod.ts`. En producción la URL pública se inyecta **en el build** (`PUBLIC_URL`/`BFF_PUBLIC_URL`), por lo que cambiar el dominio exige reconstruir la imagen del frontend.

### 2. BFF (`bff`)
- **`SecurityConfig`:** valida el JWT de Entra ID y aplica autorización por rol/método sobre `/api/bff/**`.
- **`ProxyController`:** reenvía `/api/bff/{productos,pedidos,carrito,notificaciones,analitica,usuarios,chat}/**` a su microservicio.
- **`BffEntraSyncController`:** `POST /api/bff/auth/entra-sync` (aprovisionamiento JIT de usuarios Entra ID).
- **`HealthController`:** `/health` y `/ready` (readiness consulta los servicios internos).

### 3. `usuario` (`:8081`)
- **`AuthController`** (`/api/auth`): login, registro y recuperación de contraseña.
- **`security/JwtUtil`:** emite y valida el JWT local (HMAC, secreto `jwt.secret`/`JWT_SECRET`) con ID, correo y rol (`CLIENTE`, `REPARTIDOR`, `VENDEDOR`, `ADMIN`). Contraseñas con BCrypt.
- **`InternalController`:** endpoints servicio-a-servicio (clave `X-Internal-Service-Key`).
- **`UsuarioService`:** además del *seed* de usuarios demo, publica los eventos de login exitoso/fallido y de reseteo de contraseña a RabbitMQ.

### 4. `pedidos` (`:8082`)
- **`PedidoController`** (`/api/pedidos/**`): matriz de autorización por rol (el cliente solo ve sus pedidos; el repartidor gestiona sus asignaciones; el admin accede a todo).
- **`InternalPedidoController`** (`/api/internal/pedidos/{id}`): consulta S2S (la usa `chat`), protegida por **`ServiceKeyFilter`** (valida `X-Internal-Service-Key`).
- **`PedidoService`:** publica `pedido.creado`, `pedido.estado.actualizado` y `pedido.repartidor.asignado` en RabbitMQ.

### 5. `chat` (`:8091`)
- **`ChatController`** (`/api/chat`): `POST /conversacion`, `GET /pedido/{pedidoId}`, `GET /usuario/{usuarioId}`, `POST /mensaje`, `DELETE /conversacion/{id}`.
- **`WebSocketConfig`:** endpoint `/ws-chat` (STOMP, con y sin SockJS), broker `/topic` y `/queue`. Un interceptor del canal entrante **exige JWT en `CONNECT`** y valida que el usuario participe de la conversación antes de permitir `SUBSCRIBE` a `/topic/chat/{id}`.
- **`ChatService`:** reglas de conversación; consulta el pedido a `pedidos` por S2S.
- **`PedidoEventListener`:** consume `pedido.estado.actualizado`; al pasar a `ENTREGADO` cierra la conversación.
- **`RetentionService`:** purga diaria (`@Scheduled`, 03:00) de conversaciones cerradas más antiguas que `CHAT_RETENTION_DAYS`.

### 6. `carrito`, `productos`, `analitica`, `notificacion`
- **`carrito`:** carrito por usuario; `CarritoExpiracionService` expira carritos y publica `carrito.expirado`; consume `pedido.creado`.
- **`productos`:** catálogo, stock y precios.
- **`analitica`:** consume **todos** los eventos (`#`) y los persiste como métricas.
- **`notificacion`:** consume `pedido.#`, `carrito.#` y `usuario.#` y genera notificaciones por usuario.

---

## 📨 Eventos Asíncronos (RabbitMQ)

Exchange de tipo *topic* **`pedidos360.events`** (durable). Cada consumidor declara su cola con *dead-letter*.

| Routing key | Publica | Consumen |
| :--- | :--- | :--- |
| `pedido.creado` | `pedidos` | `carrito`, `notificacion`, `analitica` |
| `pedido.estado.actualizado` | `pedidos` | `chat`, `notificacion`, `analitica` |
| `pedido.repartidor.asignado` | `pedidos` | `notificacion`, `analitica` |
| `carrito.expirado` | `carrito` | `notificacion`, `analitica` |
| `usuario.login.exitoso` / `usuario.login.fallido` | `usuario` | `analitica` |
| `usuario.password.reset` | `usuario` | `notificacion` |
| `chat.creado` / `chat.mensaje.enviado` | `chat` | sin consumidor específico (la cola de analítica recibe todo por el binding `#`) |

Colas: `chat.pedido.estado.queue` (DLX `chat.dlx` → `chat.dlq`), `analytics.queue` (→ `analytics.dlq`), `notifications.queue` (→ `notifications.dlq`) y la cola de `carrito` (DLX `pedidos360.dlx`, clave `cart.dead-letter`). Un servicio caído no frena a los demás: los mensajes quedan en cola hasta que vuelva.

> En desarrollo, el panel de RabbitMQ está en `http://localhost:15672`.

---

## 🐳 Microservicios & Contenedores (Docker Compose)

| Servicio | Puerto (dev) | Responsabilidad | Base de datos |
| :--- | :--- | :--- | :--- |
| **Caddy** *(solo EC2)* | `80, 443` | Reverse proxy HTTPS, TLS automático (Let's Encrypt sobre sslip.io), WebSocket `/ws-chat`. | N/A |
| **Frontend (nginx)** *(solo EC2)* | — | SPA Angular 21 (clientes, repartidores, vendedores, admin). En dev se usa `npm start` en `:4200`. | N/A |
| **bff** | `8090` | Puerta de entrada para Microsoft Entra ID; autorización por rol y proxy a microservicios. | N/A |
| **usuario** | `8081` | Login/registro, JWT local, usuarios y roles. | `usuario_db` |
| **pedidos** | `8082` | Pedidos, autorización por rol, S2S, eventos. | `pedidos_db` |
| **carrito** | `8083` | Carrito con validación de propiedad; expiración. | `carrito_db` |
| **analitica** | `8084` | Métricas a partir de eventos. | `analitica_db` |
| **productos** | `8085` | Catálogo, stock y precios. | `productos_db` |
| **notificacion** | `8086` | Notificaciones por usuario. | `notificacion_db` |
| **chat** | `8091` | Conversaciones, STOMP/WebSocket, purga programada. | `chat_db` |
| **PostgreSQL 16** | `5432` | Una base por servicio (se crean con `init-dbs.sql`). | Multi-DB |
| **pgAdmin 4** | `5050` *(solo dev; en EC2 no se publica)* | Administración visual de la BD. | — |
| **RabbitMQ** | `5672 / 15672` | Broker de eventos y colas *dead-letter*. | N/A |

---

## 👥 Roles del Sistema & Permisos

| Rol | Permisos principales | Rutas del frontend |
| :--- | :--- | :--- |
| **ADMIN** | Control total: analítica, productos, pedidos y usuarios. | todas (`/analitica` y `/personal` son exclusivas de ADMIN) |
| **VENDEDOR** | Gestión de productos y pedidos recibidos. | `/vendedor` |
| **REPARTIDOR** | Ver y tomar pedidos disponibles, marcar entregado, chatear con el cliente. | `/repartidor` |
| **CLIENTE** | Catálogo, carrito, pedidos, seguimiento y chat con su repartidor. | `/`, `/carrito`, `/cliente` |

Todos los roles acceden a `/cuenta`, `/perfil` y `/notificaciones`.

### Credenciales de demo (usuarios semilla del login local)

`usuario-service` crea estos usuarios al arrancar (`UsuarioService`). **Son datos de demostración**:

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin@pedidos360.cl` | `Password123!` | ADMIN |
| `vendedor@pedidos360.cl` | `Password123!` | VENDEDOR |
| `camila.reyes@pedidos360.cl` | `Password123!` | VENDEDOR (soporte) |
| `rodrigo.morales@pedidos360.cl` | `Password123!` | REPARTIDOR |
| `cliente@pedidos360.cl` | `Password123!` | CLIENTE |

> ⚠️ Estas credenciales están publicadas en este repositorio. Úsalas solo en desarrollo/demo; en un despliegue con datos reales hay que deshabilitar el *seed* o cambiar las contraseñas (ver [Seguridad](#-seguridad-pendientes-conocidos)).

---

## 🔧 Variables de Entorno

Plantillas: `Backend/.env.example` (dev) y `Backend/env/prod/.env.example` (prod). Para EC2 el despliegue usa un `.env.production` (lo genera `scripts/deploy-ec2.sh`).

| Variable | Uso |
| :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `dev` (por defecto) o `prod` (el overlay de EC2 lo fija en `prod`: `ddl-auto=validate`, sin SQL en logs). |
| `PUBLIC_DOMAIN` / `PUBLIC_URL` | Dominio (`3-92-44-37.sslip.io`) y URL con esquema (`https://…`) públicos. Los usa Caddy y el *build* del frontend. |
| `BFF_PUBLIC_URL` | URL base del BFF que usará el frontend (API Gateway o `PUBLIC_URL/api/bff`). |
| `FRONTEND_ORIGIN` | Origen permitido por CORS. |
| `BFF_INTERNAL_KEY` | Clave compartida para llamadas servicio-a-servicio (`X-Internal-Service-Key`). |
| `JWT_SECRET` | Secreto HMAC del JWT local. **Debe ser el mismo en todos los servicios que emiten/validan el token.** |
| `AZURE_TENANT_ID` / `AZURE_API_CLIENT_ID` | Identificadores públicos del tenant y de la API en Entra ID. |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Credenciales de PostgreSQL. |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | Credenciales de RabbitMQ (por defecto `guest/guest`). |
| `CHAT_RETENTION_DAYS` | Días de retención de conversaciones cerradas (30 por defecto). |

---

## 🗄️ Migraciones de Base de Datos (Flyway)

Cada servicio versiona su esquema con Flyway (`ddl-auto=validate` en producción):

- **`usuario`:** `V1__init_schema.sql`, `V2__agregar_rol_repartidor.sql`
- **`pedidos`:** `V1__init_schema.sql`, `V2__agregar_columna_repartidor.sql`
- **`chat`:** `V1__init_chat_schema.sql`
- **`carrito`, `productos`, `analitica`, `notificacion`:** `V1__init_schema.sql`

---

## 🛠️ Ejecución Local Rápida

```bash
# 1. Clonar
git clone https://github.com/vixhin/pedidos360.git
cd pedidos360/Backend

# 2. Variables de entorno (ajusta BFF_INTERNAL_KEY y credenciales)
cp .env.example .env

# 3. Levantar todo el stack: PostgreSQL, pgAdmin, RabbitMQ, 7 microservicios y BFF
docker compose up --build -d
#    (solo infraestructura: docker compose up -d postgres pgadmin rabbitmq)

# 4. Frontend Angular
cd ../pedidos360-frontend
npm install
npm start          # http://localhost:4200
```

> Cada servicio Java tarda ~1–2 min en arrancar la primera vez. Revisa el estado con `docker compose ps`. pgAdmin: `http://localhost:5050`. Guía de la API por microservicio: [`Backend/README.md`](Backend/README.md).

---

## ☁️ Infraestructura como Código (Terraform en AWS)

Definida en `terraform/` con módulos reutilizables y entornos independientes (`dev`, `prod`):

```
Terraform
   ├──▶ network         VPC, subred pública, Internet Gateway, rutas
   ├──▶ security_group  Ingress 80/443 (0.0.0.0/0) y 22 (solo allowed_ssh_cidr)
   ├──▶ elastic_ip      IP pública fija asociada a la EC2
   ├──▶ ec2             Ubuntu 22.04 + Docker Engine + Compose plugin (user_data)
   └──▶ api_gateway     HTTP API v2 → proxy a Caddy/BFF (ANY /api/bff/{proxy+}), CORS y throttling
```

- **Mínimo privilegio:** solo `80`, `443` y `22` (restringido). PostgreSQL, RabbitMQ, BFF y microservicios no son alcanzables desde Internet.
- **IP estable:** la Elastic IP evita que la IP cambie al detener/iniciar la instancia (`stop`/`start`), y de ahí el dominio `<ip-con-guiones>.sslip.io` usado por Caddy para el certificado.
- El estado de Terraform es **local** (`terraform.tfstate`, ignorado por git). Detalle de comandos, `stop` vs `destroy` y rollback: [`terraform/README.md`](terraform/README.md).

---

## 🚀 Despliegue en AWS EC2

El stack de producción se levanta con `docker-compose.yml` + el overlay `docker-compose.ec2.yml` (agrega Caddy y el frontend, deja los demás servicios sin puertos publicados y activa el perfil `prod`).

### Opción A — Terraform + script (recomendada)

```bash
cd terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars   # define allowed_ssh_cidr = "<tu-ip>/32"
terraform init && terraform apply

# Exporta los secretos ANTES de desplegar (si no, el script usa valores por defecto públicos)
export POSTGRES_PASSWORD=... RABBITMQ_PASSWORD=... BFF_INTERNAL_KEY=... JWT_SECRET=...
./scripts/deploy-ec2.sh dev ~/.ssh/<llave>.pem      # en Windows: .\scripts\deploy-ec2.ps1
```

El script obtiene la IP desde `terraform output`, genera `PUBLIC_DOMAIN`/`PUBLIC_URL`, clona o actualiza el repo en `/opt/pedidos360/app`, escribe `Backend/.env.production` y ejecuta `docker compose … up -d --build`.

### Opción B — EC2 ya creada (despliegue manual)

1. **Security Group** de la instancia: solo `22`, `80` y `443` (no abrir `5050`, `8081-8090`, `4200`, `5432`, `5672`).
2. Conéctate por SSH (usuario `ubuntu` en AMI Ubuntu) e ingresa a la carpeta del proyecto:
   ```bash
   ssh -i <llave>.pem ubuntu@<IP-PUBLICA>
   cd ~/pedidos360/Backend          # o /opt/pedidos360/app/Backend si lo creó el script
   git fetch origin && git pull     # asegúrate de tener el último commit (RabbitMQ, chat, etc.)
   ```
3. Define el dominio con la IP pública (guiones en vez de puntos) y levanta:
   ```bash
   export PUBLIC_DOMAIN=3-92-44-37.sslip.io
   export PUBLIC_URL=https://3-92-44-37.sslip.io
   export BFF_INTERNAL_KEY=... JWT_SECRET=...        # o usa --env-file .env.production
   docker compose -f docker-compose.yml -f docker-compose.ec2.yml up -d --build
   ```
4. Espera 1–2 minutos (arrancan 8 JVM) y verifica: `docker ps` (debe aparecer `pedidos360-rabbitmq` *healthy*) y `curl -I https://$PUBLIC_DOMAIN`.

### API Gateway (opcional, desde la consola de AWS)

*API Gateway → Create API → HTTP API*: integración **HTTP** `ANY` hacia `https://<dominio>/{proxy}` (usar el dominio sslip.io, **no la IP**, para que valide el certificado de Caddy), ruta `ANY /{proxy+}` y stage `$default` con auto-deploy. Con Terraform el módulo `api_gateway` crea una API equivalente restringida a `ANY /api/bff/{proxy+}`.

### ✅ Checklist cuando cambia la IP pública

Ocurre si la instancia no tiene Elastic IP (por ejemplo, al reiniciar un AWS Academy Learner Lab):

1. Anota la IP nueva y calcula `PUBLIC_DOMAIN` (`3-92-44-37.sslip.io`).
2. Re-despliega con las variables nuevas (paso 3 de la opción B). El **frontend debe reconstruirse**: la URL queda incrustada en el JavaScript compilado.
3. **Microsoft Entra ID:** *Registros de aplicaciones → `Pedidos360-SPA` → Authentication* → agrega el *Redirect URI* `https://<dominio>` (la app `Pedidos360-API` no necesita URI de redirección).
4. **API Gateway:** edita la integración y actualiza la URL a `https://<dominio nuevo>/{proxy}`.
5. Pruebas e2e: `PLAYWRIGHT_BASE_URL=https://<dominio nuevo>`.

### Administrar la base de datos en la EC2

PostgreSQL, pgAdmin y RabbitMQ **no se publican** en la EC2. Para inspeccionar datos usa la consola del contenedor, sin abrir puertos al exterior:

```bash
docker exec -it pedidos360-postgres psql -U postgres -d usuario_db
docker logs pedidos360-<servicio> --tail 100
```

---

## 🧪 Pruebas y Calidad

| Tipo | Herramienta | Cómo ejecutarla |
| :--- | :--- | :--- |
| Unitarias / integración (caja blanca) | JUnit 5 + Mockito, JaCoCo | `cd Backend/<servicio> && ./mvnw test` (Windows: `.\mvnw test`). Reporte de cobertura: `target/site/jacoco/index.html`. |
| E2E (caja negra) | Playwright (`pedidos360-frontend/e2e/`: 13 casos TC-01…TC-13) | Ver abajo. |
| Carga / estrés | Apache JMeter | Se midió contra el entorno desplegado (resultados de referencia abajo). |
| Rendimiento web | Google Lighthouse | Desde Chrome DevTools o CLI sobre la URL pública. |

El repositorio contiene **157** casos `@Test` repartidos en los 8 módulos Java (los ejecuta el CI).

**E2E contra un entorno desplegado** (requiere `npx playwright install chromium` la primera vez):

```bash
cd pedidos360-frontend
npm ci
PLAYWRIGHT_BASE_URL=https://<dominio> npx playwright test --config=playwright.config.ec2.ts
# PowerShell: $env:PLAYWRIGHT_BASE_URL="https://<dominio>"; npx playwright test --config=playwright.config.ec2.ts
```

Reporte HTML en `playwright-report-ec2/` (`npx playwright show-report playwright-report-ec2`). Ojo: `TC-06` registra un usuario nuevo en el entorno que se pruebe. `playwright.config.ts` (sin sufijo) levanta `ng serve` y prueba en `localhost:4200`.

**Mediciones de referencia** (11-sep-2026, EC2 `t3.medium` de Learner Lab, *antes* de integrar RabbitMQ y chat; conviene volver a medir):

| Prueba | Resultado |
| :--- | :--- |
| Playwright | 13/13 casos exitosos contra la EC2 |
| Lighthouse (inicio) | Performance 74 móvil / 68 escritorio · Accessibility 90 · Best Practices 100 · SEO 73 |
| JMeter, 100 usuarios concurrentes (login + catálogo + carrito) | 27,5 % de error, p95 ≈ 21 s: saturación de conexiones de la instancia y errores 500 en `carrito` por agotamiento del pool de BD |

---

## 🩺 Solución de Problemas

| Síntoma | Causa probable y solución |
| :--- | :--- |
| `502 Bad Gateway` en `/api/*` justo después de desplegar | Los microservicios aún están arrancando (~1–2 min con 8 JVM). Revisa `docker ps` y los logs de Caddy (`connection refused` = el servicio todavía no escucha); reintenta. |
| Error TLS / `internal_error` al abrir el dominio | El certificado se pidió para otra IP. Re-despliega con el `PUBLIC_DOMAIN` actual y revisa `docker logs pedidos360-caddy`. |
| `pedidos360-rabbitmq` no aparece en `docker ps -a` | La EC2 tiene código antiguo: `git fetch origin && git pull` y volver a ejecutar `docker compose … up -d --build`. |
| Login con Microsoft falla tras reiniciar el lab | Falta el *Redirect URI* de la IP nueva en Entra ID o el frontend no se reconstruyó con la URL nueva. |
| API Gateway devuelve `503` | La integración sigue apuntando a la IP/dominio anterior. |
| Playwright: `Executable doesn't exist` | Ejecuta `npx playwright install chromium`. |
| PowerShell: `mvnw no se reconoce` | Usa `.\mvnw` (PowerShell no ejecuta comandos de la carpeta actual sin `.\`). |

---

## 🚀 Integración Continua (GitHub Actions)

- **`ci.yml`** (push/PR a `main` y `develop`): `./mvnw test` en los 8 módulos Java (JDK 21), `npm ci && npm run build` del frontend, `terraform fmt -check` + `terraform validate` (dev y prod) y `docker compose config` del stack local y del overlay EC2.
- **`cd.yml`** (manual, `workflow_dispatch`): `terraform init/validate/plan` del entorno elegido. No hace `apply` mientras el estado de Terraform sea local.

---

## 🔒 Seguridad: Pendientes Conocidos

Hallazgos verificados en el código actual (pensados como lista de trabajo, no como críticas):

1. **Secretos con valor por defecto en un repositorio público.** `jwt.secret` tiene un valor por defecto en `usuario`, `pedidos` y `chat`; `BFF_INTERNAL_KEY` también; `docker-compose.yml` fija `postgres/postgres` y pgAdmin `admin/admin`; y `scripts/deploy-ec2.sh` usa valores de respaldo si no se exportan los secretos. Cualquier despliegue que no los sobrescriba usa secretos conocidos. → Exigir variables sin *fallback* en `prod`, rotar los valores y no publicar credenciales reales.
2. **Propagación incompleta de secretos en `docker-compose.ec2.yml`.** `JWT_SECRET` solo se inyecta a `usuario` y `chat` (y `BFF_INTERNAL_KEY` no llega a `pedidos`, que valida JWT local y la clave S2S con sus valores por defecto). Conviene propagar las mismas variables a **todos** los servicios que validan el token o la clave interna y comprobar el flujo con un login local en la EC2.
3. **Usuarios semilla y credenciales de demo públicas** (ver arriba): deshabilitar el *seed* o forzar cambio de contraseña en entornos reales.
4. **Contraseñas de BD fijas** (`DB_PASSWORD: postgres`) en el compose base; el overlay solo las parametriza para `chat`.
5. **Security Group:** mantener únicamente `22` (restringido a tu IP), `80` y `443`.

---

## ⚠️ Mejoras Pendientes

- **Autorización fina** en `carrito`, `productos`, `notificacion` y `analitica` (hoy depende sobre todo del BFF).
- **Concurrencia de repartidores:** bloqueo optimista (`@Version`) o *update* condicional cuando varios toman el mismo pedido.
- **Terraform:** backend remoto (S3 + DynamoDB) en lugar de estado local.
- **API Gateway:** unificar el gateway de Terraform (solo `/api/bff/*`) con el que se crea manualmente (`/{proxy+}`), y conectar todo el frontend a su endpoint.
- **Documentación de la API:** no hay OpenAPI/Swagger; agregar `springdoc` o una colección Postman versionada.
- **Pruebas:** versionar el plan de JMeter (sin credenciales) y volver a medir carga con RabbitMQ y chat activos.
